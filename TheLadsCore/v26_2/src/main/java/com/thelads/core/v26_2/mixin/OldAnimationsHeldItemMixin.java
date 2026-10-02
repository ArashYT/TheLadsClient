package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v26_2.feature.NativeOldAnimations;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public class OldAnimationsHeldItemMixin {
    @Unique private static final PoseStack.Pose lads$handPivot = new PoseStack.Pose();

    // 1.7 Animations' third person: the arm pivot, right after vanilla's (and any model mod's) translateToHand...
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/client/model/ArmedModel;"
        + "translateToHand(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V"),
        require = 1)
    private void lads$handPivot(CallbackInfo callback, @Local(argsOnly = true) PoseStack pose) {
        lads$handPivot.set(pose.last());
    }

    // ...where a flat held item is drawn in 1.7's placement instead of vanilla's.
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit("
        + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"), require = 1)
    private void lads$oldHeldItem(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline,
                                  Operation<Void> original, @Local(argsOnly = true) ArmedEntityRenderState state,
                                  @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) HumanoidArm arm) {
        NativeOldAnimations.thirdPersonItem(state, stack, arm, pose, lads$handPivot);
        original.call(item, pose, collector, light, overlay, outline);
    }
}
