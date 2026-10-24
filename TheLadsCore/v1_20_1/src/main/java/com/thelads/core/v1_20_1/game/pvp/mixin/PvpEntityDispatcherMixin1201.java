package com.thelads.core.v1_20_1.game.pvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.PvpVisuals;
import com.thelads.core.v1_20_1.game.pvp.PvpVisuals1201;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hitboxes: shown while the module is on, F3+B or not (invisible entities and reduced debug info still hide them, as in vanilla),
 * and drawn in the module's style. Hide Mob Deaths: a dying mob is culled like an entity off screen.
 */
@Mixin(EntityRenderDispatcher.class)
public class PvpEntityDispatcherMixin1201 {
    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderHitBoxes:Z"))
    private boolean lads$showHitboxes(boolean f3b) {
        return f3b || PvpVisuals.HITBOXES.on;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;"
        + "renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;F)V"))
    private void lads$hitbox(PoseStack pose, VertexConsumer lines, Entity entity, float partial, Operation<Void> original,
                             @Local(argsOnly = true) MultiBufferSource buffers) {
        if (!PvpVisuals1201.hitbox(pose, buffers, entity, partial)) original.call(pose, lines, entity, partial);
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void lads$hideMobDeath(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (PvpVisuals1201.hidden(entity)) cir.setReturnValue(false);
    }
}
