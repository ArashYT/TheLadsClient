package com.thelads.smp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.smp.features.NetherPortalFixManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/**
 * Consolidates 'netherportalfix': Redirects exit portal lookup to the player's tracked return portal when available.
 */
@Mixin(NetherPortalBlock.class)
public abstract class MixinNetherPortalBlock {

    @ModifyExpressionValue(
            method = "getExitPortal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/level/border/WorldBorder;)Lnet/minecraft/world/level/portal/TeleportTransition;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/portal/PortalForcer;findClosestPortalPosition(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/level/border/WorldBorder;)Ljava/util/Optional;"
            )
    )
    private Optional<BlockPos> ladssmp$resolveReturnPortal(
            Optional<BlockPos> prevResult,
            ServerLevel destination,
            Entity entity,
            BlockPos pos,
            BlockPos exitPos,
            boolean isNether,
            WorldBorder worldBorder
    ) {
        return NetherPortalFixManager.resolveExitPortal(prevResult, destination, entity, pos);
    }
}
