package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.modules.FreelookModule;
import com.thelads.core.v1_8_9.feature.PvpView189;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityAnimal;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Freelook on 1.8.9: the mouse turns Freelook's camera instead of the player (both setAngles calls: smooth camera and normal), and
 * orientCamera reads the camera's yaw and pitch instead of the view entity's (third-person distance ray, rotations and Forge's
 * CameraSetup event alike), current and previous the same so the frame is not interpolated back. A camera on an animal (spectating
 * one) takes its yaw from the head: that too. Every target is in vanilla's, OptiFine HD U M5's and M6 pre2's orientCamera and
 * updateCameraAndRender (checked in their patched EntityRenderer bytecode), so require = 1 cannot fail with either installed.
 */
@Mixin(EntityRenderer.class)
public abstract class FreelookCameraMixin189 {
    @Redirect(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;setAngles(FF)V"),
        require = 1)
    private void lads$freelookTurn(EntityPlayerSP player, float yaw, float pitch) {
        if (!PvpView189.turn(yaw, pitch)) player.setAngles(yaw, pitch);
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F", opcode = Opcodes.GETFIELD), require = 1)
    private float lads$freelookYaw(Entity entity) {
        FreelookModule look = FreelookModule.get();
        return look.detached() ? look.yaw() : entity.rotationYaw;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F", opcode = Opcodes.GETFIELD), require = 1)
    private float lads$freelookPrevYaw(Entity entity) {
        FreelookModule look = FreelookModule.get();
        return look.detached() ? look.yaw() : entity.prevRotationYaw;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationPitch:F", opcode = Opcodes.GETFIELD), require = 1)
    private float lads$freelookPitch(Entity entity) {
        FreelookModule look = FreelookModule.get();
        return look.detached() ? look.pitch() : entity.rotationPitch;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F", opcode = Opcodes.GETFIELD), require = 1)
    private float lads$freelookPrevPitch(Entity entity) {
        FreelookModule look = FreelookModule.get();
        return look.detached() ? look.pitch() : entity.prevRotationPitch;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/passive/EntityAnimal;rotationYawHead:F",
        opcode = Opcodes.GETFIELD), require = 1)
    private float lads$freelookAnimalYaw(EntityAnimal animal) {
        FreelookModule look = FreelookModule.get();
        return look.detached() ? look.yaw() : animal.rotationYawHead;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/passive/EntityAnimal;prevRotationYawHead:F",
        opcode = Opcodes.GETFIELD), require = 1)
    private float lads$freelookAnimalPrevYaw(EntityAnimal animal) {
        FreelookModule look = FreelookModule.get();
        return look.detached() ? look.yaw() : animal.prevRotationYawHead;
    }
}
