package com.thelads.smp.features.arise.nb1.mixin;

import com.thelads.smp.features.arise.nb1.AdminClaimsFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin target: net.minecraft.world.level.ServerExplosion
 * Method: private void interactWithBlocks(java.util.List<net.minecraft.core.BlockPos>);
 */
@Mixin(ServerExplosion.class)
public abstract class MixinExplosionClaims {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void ladssmp$protectClaimedChunks(List<BlockPos> positions, CallbackInfo ci) {
        if (AdminClaimsFeature.isEnabled()) {
            positions.removeIf(pos -> AdminClaimsFeature.isProtectedAgainstExplosion(this.level, pos));
        }
    }
}
