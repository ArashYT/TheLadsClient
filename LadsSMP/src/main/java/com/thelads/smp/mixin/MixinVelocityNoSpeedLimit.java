package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Consolidates 'imfast': relaxes the 'moved too quickly' thresholds in ServerGamePacketListenerImpl to cut false
 * rubberbanding. Bounded on purpose: the vanilla checks still run with RELAX x the limit (they are the only
 * per-packet distance guard before ServerPlayer.move), and the 'moved wrongly' (0.0625) collision check is never
 * touched, so teleport, phasing and the huge-delta collision sweep (server hang) stay blocked.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 2000)
public abstract class MixinVelocityNoSpeedLimit {
    @Unique
    private static final float LADSSMP_RELAX = 4.0F;

    @ModifyConstant(method = "handlePlayerPositionChange", constant = @Constant(floatValue = 100.0F))
    private float ladssmp$noSpeedLimitPlayer(float speed) {
        return LadsConfig.get().features.noSpeedLimit ? speed * LADSSMP_RELAX : speed;
    }

    @ModifyConstant(method = "handlePlayerPositionChange", constant = @Constant(floatValue = 300.0F))
    private float ladssmp$noSpeedLimitElytra(float speed) {
        return LadsConfig.get().features.noSpeedLimit ? speed * LADSSMP_RELAX : speed;
    }

    @ModifyConstant(method = "handleMoveVehicle", constant = @Constant(doubleValue = 100.0D))
    private double ladssmp$noSpeedLimitVehicle(double speed) {
        return LadsConfig.get().features.noSpeedLimit ? speed * LADSSMP_RELAX : speed;
    }
}
