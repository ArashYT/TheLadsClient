package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.v1_8_9.gui.LadsButtonPress;
import java.io.IOException;
import com.thelads.core.v1_8_9.feature.Chat189;
import com.thelads.core.v1_8_9.feature.Tooltips189;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Lads backdrop instead of the dirt texture behind menus outside a world; LadsButtonPress for the More screens. */
@Mixin(GuiScreen.class)
public abstract class GuiScreenMixin extends Gui implements LadsButtonPress {
    @Shadow public int width, height;
    @Shadow protected List<GuiButton> buttonList;
    @Shadow protected abstract void actionPerformed(GuiButton button) throws IOException;
    @Shadow public net.minecraft.client.Minecraft mc;

    /**
     * Two servers clicked fast joined both: a tick's queued clicks all went to the screen open when it started, so a click behind the
     * one that opened GuiConnecting still reached the server list and started a second connection. Once another screen replaced this
     * one, the rest of the queue waits for that screen (1.20.1+ hand every event to the current screen). Closing to the game keeps
     * vanilla's behaviour (the old screen takes the rest), so no queued menu click becomes an attack.
     */
    @org.spongepowered.asm.mixin.injection.Redirect(method = "handleInput", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;next()Z", remap = false), require = 1)
    private boolean ladsMouseNext() { return ladsStillCurrent() && org.lwjgl.input.Mouse.next(); }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "handleInput", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;next()Z", remap = false), require = 1)
    private boolean ladsKeyboardNext() { return ladsStillCurrent() && org.lwjgl.input.Keyboard.next(); }

    @org.spongepowered.asm.mixin.Unique
    private boolean ladsStillCurrent() {
        GuiScreen now = mc == null ? null : mc.currentScreen;
        return now == null || now == (Object) this;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ladsToastClick(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (com.thelads.core.client.hud.ServerRulesNotice.mouseClicked(mouseX, mouseY, mouseButton)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleComponentClick", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsHandleChatClick(IChatComponent component, CallbackInfoReturnable<Boolean> cir) {
        if (Chat189.handleClick(component)) {
            cir.setReturnValue(true);
        }
    }

    /** Tooltips too big for the screen are drawn smaller (Tooltips189.drawFitted). Forge's overload, which every tooltip goes through. */
    @Inject(method = "drawHoveringText(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void ladsFitTooltip(List<String> lines, int x, int y, FontRenderer font, CallbackInfo ci) {
        if (Tooltips189.drawFitted(lines, x, y, width, height, font)) ci.cancel();
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsBackdrop(int tint, CallbackInfo ci) {
        if ((Object) this instanceof net.minecraft.client.gui.GuiScreenResourcePacks && com.thelads.core.client.pack.PackMenuSettings.isTransparent()) {
            ci.cancel();
            return;
        }
        drawGradientRect(0, 0, width, height, LadsPalette.BACKGROUND, 0xFF30111C);
        ci.cancel();
    }

    /** As GuiScreen.mouseClicked does for a clicked button: Forge's action events around the screen's own actionPerformed. */
    @Override
    public void ladsPress(GuiButton button) {
        GuiScreen screen = (GuiScreen) (Object) this;
        ActionPerformedEvent.Pre event = new ActionPerformedEvent.Pre(screen, button, buttonList);
        if (MinecraftForge.EVENT_BUS.post(event)) return;
        try {
            actionPerformed(event.button);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        MinecraftForge.EVENT_BUS.post(new ActionPerformedEvent.Post(screen, event.button, buttonList));
    }
}
