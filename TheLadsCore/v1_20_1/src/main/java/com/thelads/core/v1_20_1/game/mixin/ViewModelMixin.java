package com.thelads.core.v1_20_1.game.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.core.modules.ViewModelModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ViewModel on 1.20.1: right after renderArmWithItem pushes the hand's pose (before the item, map or bare arm is placed), the offset,
 * then the rotation and scale around vanilla's item position, mirrored for the left hand. Hand FOV: NativeFeatures1201.fov.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ViewModelMixin {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0,
        shift = At.Shift.AFTER))
    private void lads$viewModel(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
            float equip, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ViewModelModule vm = ViewModelModule.get();
        if (!vm.posed()) return;
        float side = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT) ? 1 : -1;
        pose.translate(side * (float) vm.x.getValue(), (float) vm.y.getValue(), (float) vm.z.getValue());
        pose.translate(side * ViewModelModule.PIVOT_X, ViewModelModule.PIVOT_Y, ViewModelModule.PIVOT_Z);
        pose.mulPose(Axis.YP.rotationDegrees(side * (float) vm.rotY.getValue()));
        pose.mulPose(Axis.XP.rotationDegrees((float) vm.rotX.getValue()));
        pose.mulPose(Axis.ZP.rotationDegrees(side * (float) vm.rotZ.getValue()));
        float scale = (float) (vm.scale.getValue() / 100);
        pose.scale(scale, scale, scale);
        pose.translate(-side * ViewModelModule.PIVOT_X, -ViewModelModule.PIVOT_Y, -ViewModelModule.PIVOT_Z);
    }
}
