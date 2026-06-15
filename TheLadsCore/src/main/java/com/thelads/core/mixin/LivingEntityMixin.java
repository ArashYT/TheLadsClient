package com.thelads.core.mixin;

import com.thelads.core.modules.killbanner.KillTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"), require = 0)
    private void onHandleEntityEvent(byte eventId, CallbackInfo ci) {
        if (eventId == 3) { // 3 is the death status byte in Minecraft
            KillTracker.onEntityDeath((Entity) (Object) this);
        }
    }
}
