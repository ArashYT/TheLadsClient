package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Options189;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FarBlockEntities, as 26.x FarBlockEntitiesMixin: only the inherited render distance grows (64 blocks in vanilla) to the
 * module's Distance; renderers with a longer one (beacons) keep it. Loaded chunks and render distance still limit what is drawn.
 */
@Mixin(TileEntity.class)
public abstract class TileEntityMixin {
    @Inject(method = "getMaxRenderDistanceSquared", at = @At("RETURN"), cancellable = true, require = 1)
    private void ladsFarBlockEntities(CallbackInfoReturnable<Double> cir) {
        if (!Options189.enabled("FarBlockEntities")) return;
        double distance = Math.max(64, Math.min(256, Options189.number("FarBlockEntities", "Distance", 128)));
        if (distance * distance > cir.getReturnValue()) cir.setReturnValue(distance * distance);
    }
}
