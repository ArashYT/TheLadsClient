package com.thelads.core.v1_20_1.game.pvp.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.client.PvpVisuals;
import com.thelads.core.v1_20_1.game.pvp.PvpVisuals1201;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mount Opacity: the player's mount takes vanilla's see-through path (getRenderType's translucent: the render type an invisible mob
 * shows a spectator through) at the module's opacity instead of 15%.
 */
@Mixin(LivingEntityRenderer.class)
public class PvpMountOpacityMixin1201 {
    private static final String RENDER =
        "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;"),
        index = 2)
    private boolean lads$seeThrough(boolean translucent, @Local(argsOnly = true) LivingEntity entity) {
        return translucent || PvpVisuals1201.isMount(entity);
    }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"),
        index = 7)
    private float lads$opacity(float alpha, @Local(argsOnly = true) LivingEntity entity) {
        return PvpVisuals1201.isMount(entity) ? PvpVisuals.MOUNT_OPACITY.alpha : alpha;
    }
}
