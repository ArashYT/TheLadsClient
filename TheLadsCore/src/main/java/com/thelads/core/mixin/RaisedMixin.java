package com.thelads.core.mixin;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class RaisedMixin {
    @Unique private boolean ladsRaisedHotbar = false;
    @Unique private boolean ladsRaisedXp = false;
    @Unique private boolean ladsRaisedMount = false;

    private boolean ladsIsRaised() {
        Module m = ModuleManager.getInstance().getModule("Raised");
        if (m == null || !m.isEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return false;
        try { return mc.gui.screen() instanceof ChatScreen; } catch (Exception e) { return false; }
    }

    private void ladsPushOffset(GuiGraphicsExtractor g) {
        g.pose().pushMatrix();
        Module m = ModuleManager.getInstance().getModule("Raised");
        float offset = 14.0f;
        if (m != null && m.getOption("Distance") instanceof com.thelads.core.config.SliderOption so) {
            offset = (float) so.getValue();
        }
        g.pose().translate(0, -offset);
    }

    private void ladsPopOffset(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    @Inject(method = "extractItemHotbar", at = @At("HEAD"), require = 0)
    private void beforeExtractItemHotbar(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker tickDelta, CallbackInfo ci) {
        if (ladsIsRaised()) {
            ladsRaisedHotbar = true;
            ladsPushOffset(g);
        } else {
            ladsRaisedHotbar = false;
        }
    }

    @Inject(method = "extractItemHotbar", at = @At("RETURN"), require = 0)
    private void afterExtractItemHotbar(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker tickDelta, CallbackInfo ci) {
        if (ladsRaisedHotbar) {
            ladsPopOffset(g);
            ladsRaisedHotbar = false;
        }
    }

    @Inject(method = "extractExperienceLevel", at = @At("HEAD"), require = 0)
    private void beforeExtractExperienceBar(GuiGraphicsExtractor g, int x, CallbackInfo ci) {
        if (ladsIsRaised()) {
            ladsRaisedXp = true;
            ladsPushOffset(g);
        } else {
            ladsRaisedXp = false;
        }
    }

    @Inject(method = "extractExperienceLevel", at = @At("RETURN"), require = 0)
    private void afterExtractExperienceBar(GuiGraphicsExtractor g, int x, CallbackInfo ci) {
        if (ladsRaisedXp) {
            ladsPopOffset(g);
            ladsRaisedXp = false;
        }
    }

    @Inject(method = "extractVehicleHealth", at = @At("HEAD"), require = 0)
    private void beforeExtractMountHealth(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (ladsIsRaised()) {
            ladsRaisedMount = true;
            ladsPushOffset(g);
        } else {
            ladsRaisedMount = false;
        }
    }

    @Inject(method = "extractVehicleHealth", at = @At("RETURN"), require = 0)
    private void afterExtractMountHealth(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (ladsRaisedMount) {
            ladsPopOffset(g);
            ladsRaisedMount = false;
        }
    }
}
