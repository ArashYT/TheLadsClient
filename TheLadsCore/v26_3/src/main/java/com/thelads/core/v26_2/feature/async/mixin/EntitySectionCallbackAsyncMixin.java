package com.thelads.core.v26_2.feature.async.mixin;

import com.thelads.core.v26_2.feature.async.AsyncTicking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An entity changing section or leaving the world rewrites the level's entity maps and tick list, which other workers are
 * reading: that happens on the main thread when the phase ends. A removed entity is not moved first.
 */
@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback", priority = 900)
abstract class EntitySectionCallbackAsyncMixin {
    @Shadow @Final private EntityAccess entity;

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void lads$deferMove(CallbackInfo ci) {
        if (!AsyncTicking.onWorker()) return;
        EntityInLevelCallback callback = (EntityInLevelCallback) this;
        EntityAccess moved = entity;
        AsyncTicking.defer(() -> { if (!moved.isRemoved()) callback.onMove(); });
        ci.cancel();
    }

    @Inject(method = "onRemove", at = @At("HEAD"), cancellable = true)
    private void lads$deferRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if (!AsyncTicking.onWorker()) return;
        EntityInLevelCallback callback = (EntityInLevelCallback) this;
        AsyncTicking.defer(() -> callback.onRemove(reason));
        ci.cancel();
    }
}
