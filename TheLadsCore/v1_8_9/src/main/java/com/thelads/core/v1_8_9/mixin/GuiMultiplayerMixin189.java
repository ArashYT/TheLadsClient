package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.gui.EssentialActions189;
import com.thelads.core.v1_8_9.gui.ServerDiscoveryScreen189;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMultiplayer.class)
public abstract class GuiMultiplayerMixin189 extends GuiScreen {
    @Unique private GuiButton ladsDiscoverButton;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void ladsAddDiscoverButton(CallbackInfo ci) {
        ladsPurgeEssential();
        ladsDiscoverButton = new GuiButton(0x4C414453 + 20, width - 124, 8, 116, 20, "Discover Servers");
        buttonList.add(ladsDiscoverButton);
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void ladsSuppressEssential(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        EssentialActions189.suppressOverlay(this);
        ladsPurgeEssential();
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void ladsDiscoverAction(GuiButton button, CallbackInfo ci) {
        if (button == ladsDiscoverButton) {
            mc.displayGuiScreen(new ServerDiscoveryScreen189(this));
            ci.cancel();
        }
    }

    @Unique
    private void ladsPurgeEssential() {
        for (GuiButton btn : new ArrayList<>(buttonList)) {
            if (btn != ladsDiscoverButton && EssentialActions189.isEssential(btn)) {
                btn.visible = false;
                btn.enabled = false;
                buttonList.remove(btn);
            }
        }
    }
}
