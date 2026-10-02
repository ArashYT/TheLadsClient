// Derived from Optimized Cushions Backport 1.0.0 (commit a6de9cc) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.cushions.CushionTracker;
import com.thelads.core.v26_2.embedded.cushions.OptCushion;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("TAIL"))
    private void optimizedcushions$onDataUpdated(final EntityDataAccessor<?> accessor, final CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof OptCushion && self.level() != null && self.level().isClientSide()) {
            CushionTracker.markChanged(self);
        }
    }
}
