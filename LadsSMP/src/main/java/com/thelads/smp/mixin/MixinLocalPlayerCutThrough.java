package com.thelads.smp.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.thelads.smp.config.LadsConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Predicate;

/**
 * Consolidates 'cutthrough': Allows player attacks to pass through blocks without collision shapes (e.g., tall grass, flowers)
 * and through dead/dying entities to hit living entities behind them.
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerCutThrough {

    @ModifyArg(
            method = "raycastHitResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/component/AttackRange;getClosesetHit(Lnet/minecraft/world/entity/Entity;FLjava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"
            )
    )
    private static Predicate<Entity> ladssmp$filterAliveInRaycast(Predicate<Entity> entitySelector) {
        if (!LadsConfig.get().features.cutThrough) {
            return entitySelector;
        }
        return entitySelector.and(Entity::isAlive);
    }

    @ModifyArg(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"
            )
    )
    private static Predicate<Entity> ladssmp$filterAliveInPick(Predicate<Entity> entitySelector) {
        if (!LadsConfig.get().features.cutThrough) {
            return entitySelector;
        }
        return entitySelector.and(Entity::isAlive);
    }

    @ModifyVariable(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At("STORE")
    )
    private static HitResult ladssmp$reclipWithoutOutlineOnlyBlocks(
            HitResult hitResult,
            Entity cameraEntity,
            double blockInteractionRange,
            double entityInteractionRange,
            float partialTicks,
            @Share("originalHitResult") LocalRef<HitResult> originalHitResultRef
    ) {
        if (!LadsConfig.get().features.cutThrough) {
            return hitResult;
        }
        if (hitResult.getType() == HitResult.Type.BLOCK && hitResult instanceof BlockHitResult blockHitResult) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            BlockState blockState = cameraEntity.level().getBlockState(blockPos);
            if (blockState.getCollisionShape(cameraEntity.level(), blockPos, CollisionContext.of(cameraEntity)).isEmpty()) {
                originalHitResultRef.set(hitResult);
                Vec3 startPos = cameraEntity.getEyePosition(partialTicks);
                Vec3 viewVector = cameraEntity.getViewVector(partialTicks);
                double pickRange = Math.max(blockInteractionRange, entityInteractionRange);
                Vec3 endPos = startPos.add(viewVector.x * pickRange, viewVector.y * pickRange, viewVector.z * pickRange);
                return cameraEntity.level().clip(new ClipContext(
                        startPos,
                        endPos,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        cameraEntity
                ));
            }
        }
        return hitResult;
    }

    @ModifyReturnValue(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At("TAIL")
    )
    private static HitResult ladssmp$restoreOriginalBlockHitIfNoEntityHit(
            HitResult hitResult,
            @Share("originalHitResult") LocalRef<HitResult> originalHitResultRef
    ) {
        if (hitResult.getType() != HitResult.Type.ENTITY && originalHitResultRef.get() != null) {
            return originalHitResultRef.get();
        }
        return hitResult;
    }
}
