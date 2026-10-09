package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.v1_20_1.game.anim.ArmourTint1201;
import com.thelads.core.v1_20_1.game.anim.NativeOldAnimations1201;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class OldAnimationsArmourMixin {
    // 1.7 Animations' Red armour on hurt: the layer draws through a buffer source that reddens armour pieces while the entity is hurt.
    // (Hooked on the layer's public render so it holds with Forge's own armour model path as well.)
    @ModifyVariable(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private MultiBufferSource lads$redArmour(MultiBufferSource buffers) {
        return new ArmourTint1201(buffers);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At("HEAD"))
    private void lads$armourOf(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float a, float b, float c, float d, float e, float f,
                               CallbackInfo callback) {
        NativeOldAnimations1201.armourOf(entity);
    }
}
