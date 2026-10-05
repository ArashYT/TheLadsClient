package com.thelads.core.v26_2.feature.async.mixin;

import com.thelads.core.v26_2.feature.async.AsyncTicking;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** New entities from a worker (drops, babies, projectiles) join the world when the phase ends, on the main thread. */
@Mixin(PersistentEntitySectionManager.class)
abstract class EntitySectionManagerAsyncMixin<T extends EntityAccess> {
    @Shadow public abstract boolean addNewEntity(T entity);

    @Inject(method = "addNewEntity", at = @At("HEAD"), cancellable = true)
    private void lads$deferAdd(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (!AsyncTicking.onWorker()) return;
        AsyncTicking.defer(() -> { if (!entity.isRemoved()) addNewEntity(entity); });
        cir.setReturnValue(true);
    }
}
