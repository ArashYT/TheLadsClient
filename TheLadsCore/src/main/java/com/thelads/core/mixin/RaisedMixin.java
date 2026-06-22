package com.thelads.core.mixin;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class RaisedMixin {
    private boolean ladsIsRaised() {
        Module m = ModuleManager.getInstance().getModule("Raised");
        if (m == null || !m.isEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.gui.screen() instanceof ChatScreen;
    }

    private void ladsPushOffset(GuiGraphicsExtractor g) {
        if (ladsIsRaised()) {
            g.pose().pushMatrix();
            // Raise the HUD element by 14 pixels (approximate height of chat input)
            g.pose().translate(0, -14.0f);
        }
    }

    private void ladsPopOffset(GuiGraphicsExtractor g) {
        if (ladsIsRaised()) {
            g.pose().popMatrix();
        }
    }

    @Inject(method = "extractItemHotbar", at = @At("HEAD"), require = 0)
    private void beforeExtractItemHotbar(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker tickDelta, CallbackInfo ci) {
        ladsPushOffset(g);
    }

    @Inject(method = "extractItemHotbar", at = @At("RETURN"), require = 0)
    private void afterExtractItemHotbar(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker tickDelta, CallbackInfo ci) {
        ladsPopOffset(g);
    }

    @Inject(method = "extractExperienceLevel", at = @At("HEAD"), require = 0)
    private void beforeExtractExperienceBar(GuiGraphicsExtractor g, int x, CallbackInfo ci) {
        ladsPushOffset(g);
    }

    @Inject(method = "extractExperienceLevel", at = @At("RETURN"), require = 0)
    private void afterExtractExperienceBar(GuiGraphicsExtractor g, int x, CallbackInfo ci) {
        ladsPopOffset(g);
    }

    @Inject(method = "extractVehicleHealth", at = @At("HEAD"), require = 0)
    private void beforeExtractMountHealth(GuiGraphicsExtractor g, CallbackInfo ci) {
        ladsPushOffset(g);
    }

    @Inject(method = "extractVehicleHealth", at = @At("RETURN"), require = 0)
    private void afterExtractMountHealth(GuiGraphicsExtractor g, CallbackInfo ci) {
        ladsPopOffset(g);
    }
}
