package com.thelads.core.v1_21_1.feature.qa.mixin;

import com.thelads.core.v1_21_1.feature.NativeHudProbe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every 1.21.1 sprite blit ends in innerBlit, drawn at once; the U3 HUD probe reads its size, pose and shader alpha while armed. */
@Mixin(GuiGraphics.class)
public class GuiGraphicsQaMixin {
    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V", at = @At("HEAD"))
    private void ladsQaBlit(ResourceLocation atlas, int x1, int x2, int y1, int y2, int z, float u0, float u1, float v0, float v1, CallbackInfo ci) {
        if (NativeHudProbe.recording) NativeHudProbe.blit((GuiGraphics) (Object) this, x2 - x1, y2 - y1);
    }
}
