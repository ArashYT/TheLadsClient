package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.core.modules.ViewModelModule;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ViewModel on 26.3: right after submitArmWithItem pushes the hand's pose (before the item, map or bare arm is placed), the offset,
 * then the rotation and scale around vanilla's item position, mirrored for the left hand. Hand FOV: NativeFeatures.fov.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ViewModelMixin {
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0,
        shift = At.Shift.AFTER), require = 1)
    private void lads$viewModel(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float partialTick, float pitch,
            InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light,
            CallbackInfo ci) {
        ViewModelModule vm = ViewModelModule.get();
        if (!vm.posed()) return;
        float side = (hand == InteractionHand.MAIN_HAND) == (player.avatarRenderState.mainArm == HumanoidArm.RIGHT) ? 1 : -1;
        pose.translate(side * (float) vm.x.getValue(), (float) vm.y.getValue(), (float) vm.z.getValue());
        pose.translate(side * ViewModelModule.PIVOT_X, ViewModelModule.PIVOT_Y, ViewModelModule.PIVOT_Z);
        pose.rotateDegrees(Axis.YP, side * (float) vm.rotY.getValue());
        pose.rotateDegrees(Axis.XP, (float) vm.rotX.getValue());
        pose.rotateDegrees(Axis.ZP, side * (float) vm.rotZ.getValue());
        float scale = (float) (vm.scale.getValue() / 100);
        pose.scale(scale, scale, scale);
        pose.translate(-side * ViewModelModule.PIVOT_X, -ViewModelModule.PIVOT_Y, -ViewModelModule.PIVOT_Z);
    }
}
