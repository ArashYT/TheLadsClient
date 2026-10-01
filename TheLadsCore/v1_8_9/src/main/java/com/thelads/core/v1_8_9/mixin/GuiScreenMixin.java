package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.v1_8_9.gui.LadsButtonPress;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Lads backdrop instead of the dirt texture behind menus outside a world; LadsButtonPress for the More screens. */
@Mixin(GuiScreen.class)
public abstract class GuiScreenMixin extends Gui implements LadsButtonPress {
    @Shadow public int width, height;
    @Shadow protected List<GuiButton> buttonList;
    @Shadow protected abstract void actionPerformed(GuiButton button) throws IOException;

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsBackdrop(int tint, CallbackInfo ci) {
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
