// Derived from Optimized Cushions 1.0.0 (tag 1.0.0) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Cushion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.cushions.CushionTracker;

@Mixin(Entity.class)
public class EntityMixin {
    // Covers color and the glowing/invisible/on-fire shared flags; the isClientSide
    // guard keeps integrated-server entities out of the tracker.
    @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("TAIL"))
    private void optimizedcushions$onDataUpdated(final EntityDataAccessor<?> accessor, final CallbackInfo ci) {
        if ((Object)this instanceof Cushion cushion && cushion.level() != null && cushion.level().isClientSide()) {
            CushionTracker.markChanged(cushion);
        }
    }
}
