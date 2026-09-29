package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.VerticalBob;
import com.thelads.core.v26_2.feature.VerticalBobState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class VerticalBobExtractionMixin {
    @Unique private final VerticalBob lads$motion = new VerticalBob();
    @Unique private Object lads$previousLevel;
    @Unique private Entity lads$previousEntity;
    @Shadow public abstract Entity entity();

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 1)
    private void lads$extractVerticalMotion(CameraRenderState state, float partialTick, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity cameraEntity = entity();
        if (lads$previousLevel != minecraft.level || lads$previousEntity != cameraEntity) {
            lads$motion.reset();
            lads$previousLevel = minecraft.level;
            lads$previousEntity = cameraEntity;
        }
        var player = minecraft.player;
        boolean active = player != null && cameraEntity == player && minecraft.level != null
            && !minecraft.isPaused() && minecraft.options.getCameraType().isFirstPerson()
            && minecraft.options.bobView().get() && NativeQualityOfLife.enabled("VerticalBobbing")
            && !player.isSpectator() && !player.isPassenger() && !player.isSleeping()
            && !player.isFallFlying() && !player.isSwimming() && !player.getAbilities().flying;
        double accessibility = Math.min(minecraft.options.screenEffectScale().get(), minecraft.options.fovEffectScale().get());
        double intensity = switch (NativeQualityOfLife.choice("VerticalBobbing", "Intensity", 1)) {
            case 0 -> .5;
            case 2 -> 1.5;
            default -> 1;
        };
        float displacement = lads$motion.update(player == null ? 0 : player.getDeltaMovement().y,
            player == null || player.onGround(), intensity, accessibility, System.nanoTime(), active && accessibility > 0);
        ((VerticalBobState) state).lads$verticalBob(displacement);
    }
}
