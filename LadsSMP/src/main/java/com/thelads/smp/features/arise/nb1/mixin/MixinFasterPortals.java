package com.thelads.smp.features.arise.nb1.mixin;

import com.thelads.smp.features.arise.nb1.FasterPortalsFeature;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.NetherPortalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin target: net.minecraft.world.level.block.NetherPortalBlock
 * Method: public int getPortalTransitionTime(net.minecraft.server.level.ServerLevel, net.minecraft.world.entity.Entity);
 */
@Mixin(NetherPortalBlock.class)
public abstract class MixinFasterPortals {

    @Inject(method = "getPortalTransitionTime", at = @At("HEAD"), cancellable = true)
    private void ladssmp$fasterPortalWaitTime(ServerLevel level, Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (FasterPortalsFeature.isEnabled() && entity instanceof Player player) {
            if (!player.getAbilities().invulnerable) {
                cir.setReturnValue(FasterPortalsFeature.getPortalWaitTime());
            }
        }
    }
}
