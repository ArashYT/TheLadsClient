package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Consolidates 'imfast': Removes hardcoded server speed/movement thresholds in
 * ServerGamePacketListenerImpl to eliminate false 'moved too quickly' rubberbanding.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 2000)
public abstract class MixinVelocityNoSpeedLimit {

    @ModifyConstant(method = "handlePlayerPositionChange", constant = @Constant(floatValue = 100.0F))
    private float ladssmp$noSpeedLimitPlayer(float speed) {
        return LadsConfig.get().features.noSpeedLimit ? Float.MAX_VALUE : speed;
    }

    @ModifyConstant(method = "handlePlayerPositionChange", constant = @Constant(floatValue = 300.0F))
    private float ladssmp$noSpeedLimitElytra(float speed) {
        return LadsConfig.get().features.noSpeedLimit ? Float.MAX_VALUE : speed;
    }

    @ModifyConstant(method = "handleMoveVehicle", constant = @Constant(doubleValue = 100.0D))
    private double ladssmp$noSpeedLimitVehicle(double speed) {
        return LadsConfig.get().features.noSpeedLimit ? Double.MAX_VALUE : speed;
    }

    @ModifyConstant(method = "handlePlayerPositionChange", constant = @Constant(doubleValue = 0.0625D))
    private double ladssmp$noSpeedLimitMovedWrong(double speed) {
        return LadsConfig.get().features.noSpeedLimit ? Double.MAX_VALUE : speed;
    }

    @ModifyConstant(method = "handleMoveVehicle", constant = @Constant(doubleValue = 0.0625D, ordinal = 0))
    private double ladssmp$noSpeedLimitVehicleMovedWrong(double speed) {
        return LadsConfig.get().features.noSpeedLimit ? Double.MAX_VALUE : speed;
    }
}
