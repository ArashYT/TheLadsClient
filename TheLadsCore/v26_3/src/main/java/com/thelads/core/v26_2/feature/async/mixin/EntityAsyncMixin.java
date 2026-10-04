package com.thelads.core.v26_2.feature.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.v26_2.feature.async.AsyncTicking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityAsyncMixin {
    /** Mounting changes two entities and the level's navigating-mob set. */
    @WrapMethod(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z")
    private boolean lads$lockStartRiding(Entity vehicle, boolean force, boolean events, Operation<Boolean> original) {
        return AsyncTicking.locked(original, vehicle, force, events);
    }

    @WrapMethod(method = "stopRiding")
    private void lads$lockStopRiding(Operation<Void> original) {
        AsyncTicking.locked(original);
    }

    /** Portal travel loads chunks and adds tickets in another dimension; such entities tick on the main thread. */
    @Inject(method = "teleport", at = @At("HEAD"))
    private void lads$noWorkerTeleport(TeleportTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (AsyncTicking.onWorker()) throw AsyncTicking.unsafe("teleport of " + this);
    }
}
