// Derived from Optimized Cushions 1.0.0 (tag 1.0.0) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions.mixin.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Cushion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.cushions.server.CushionServerExt;
import com.thelads.core.v26_2.embedded.cushions.server.ServerLevelExt;

/** Routes cushions into {@code CushionServerTicker} instead of the vanilla entity tick list. */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public class EntityCallbacksMixin {
    @Inject(method = "onTickingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void optimizedcushions$routeTickingStart(final Entity entity, final CallbackInfo ci) {
        if (!(entity instanceof Cushion cushion)) {
            return;
        }
        ((CushionServerExt) cushion).optimizedcushions$setServerTicking(true);
        // Passenger mechanics (tickPassenger/rideTick) need the vanilla tick list.
        if (cushion.isPassenger() || !cushion.getPassengers().isEmpty()) {
            return;
        }
        ((ServerLevelExt) cushion.level()).optimizedcushions$cushionTicker().add(cushion);
        ci.cancel();
    }

    @Inject(method = "onTickingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void optimizedcushions$routeTickingEnd(final Entity entity, final CallbackInfo ci) {
        if (!(entity instanceof Cushion cushion)) {
            return;
        }
        ((CushionServerExt) cushion).optimizedcushions$setServerTicking(false);
        ((ServerLevelExt) cushion.level()).optimizedcushions$cushionTicker().remove(cushion);
        // The vanilla entityTickList.remove still runs; it is a no-op when we owned the cushion.
    }
}
