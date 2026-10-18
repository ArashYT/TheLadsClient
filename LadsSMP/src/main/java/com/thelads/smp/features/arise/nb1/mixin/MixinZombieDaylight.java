package com.thelads.smp.features.arise.nb1.mixin;

import com.thelads.smp.features.arise.nb1.WeakerDayZombieFeature;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin target: net.minecraft.world.entity.monster.zombie.Zombie
 * Method: public void tick();
 */
@Mixin(Zombie.class)
public abstract class MixinZombieDaylight {

    @Inject(method = "tick", at = @At("TAIL"))
    private void ladssmp$daylightDebuff(CallbackInfo ci) {
        Zombie zombie = (Zombie) (Object) this;
        if (zombie.tickCount % 20 == 0) {
            WeakerDayZombieFeature.applyDaylightDebuff(zombie);
        }
    }
}
