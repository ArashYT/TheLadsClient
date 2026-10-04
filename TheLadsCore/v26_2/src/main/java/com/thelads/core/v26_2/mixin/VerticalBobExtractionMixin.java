package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.NativeVerticalBob;
import com.thelads.core.v26_2.feature.VerticalBobState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class VerticalBobExtractionMixin {
    @Shadow public abstract Entity entity();

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 1)
    private void lads$extractVerticalMotion(CameraRenderState state, float partialTick, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity cameraEntity = entity();
        var player = minecraft.player;
        boolean active = player != null && cameraEntity == player && minecraft.level != null
            && !minecraft.isPaused() && minecraft.options.getCameraType().isFirstPerson()
            && minecraft.options.bobView().get() && NativeQualityOfLife.enabled("VerticalBobbing"); // NativeVerticalBob eases out flying etc.
        double accessibility = Math.min(minecraft.options.screenEffectScale().get(), minecraft.options.fovEffectScale().get());
        double intensity = switch (NativeQualityOfLife.choice("VerticalBobbing", "Intensity", 1)) {
            case 0 -> .5;
            case 2 -> 1.5;
            default -> 1;
        };
        float displacement = NativeVerticalBob.value(partialTick,active && accessibility>0) * (float)(intensity*accessibility);
        ((VerticalBobState) state).lads$verticalBob(displacement);
    }
}
