package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v26_2.feature.NativeOldAnimations;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class OldAnimationsDroppedItemMixin {
    // 1.7 Animations' 2D dropped items: flat items are resolved without a display transform...
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
        at = @At("TAIL"), require = 1)
    private void lads$extractFlatItem(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo callback) {
        NativeOldAnimations.droppedItem(entity, state);
    }

    // ...and drawn as one 1.7 icon facing the camera instead of vanilla's spinning cluster.
    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;"
            + "Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V"), require = 1)
    private void lads$submitFlatItem(PoseStack pose, SubmitNodeCollector collector, int light, ItemClusterRenderState cluster, RandomSource random,
                                     AABB box, Operation<Void> original, @Local(argsOnly = true) CameraRenderState camera) {
        if (!NativeOldAnimations.droppedItem((ItemEntityRenderState) cluster, pose, collector, light, camera.yRot))
            original.call(pose, collector, light, cluster, random, box);
    }
}
