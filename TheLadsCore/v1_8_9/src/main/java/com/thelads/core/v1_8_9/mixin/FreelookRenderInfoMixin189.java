package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.modules.FreelookModule;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.player.EntityPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Freelook on 1.8.9: particles face Freelook's camera (ActiveRenderInfo's billboard rotation comes from the player's yaw and pitch). */
@Mixin(ActiveRenderInfo.class)
public abstract class FreelookRenderInfoMixin189 {
    @Redirect(method = "updateRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;rotationYaw:F",
        opcode = Opcodes.GETFIELD), require = 1)
    private static float lads$freelookYaw(EntityPlayer player) {
        FreelookModule look = FreelookModule.get();
        return look.detached() ? look.yaw() : player.rotationYaw;
    }

    @Redirect(method = "updateRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;rotationPitch:F",
        opcode = Opcodes.GETFIELD), require = 1)
    private static float lads$freelookPitch(EntityPlayer player) {
        FreelookModule look = FreelookModule.get();
        return look.detached() ? look.pitch() : player.rotationPitch;
    }
}
