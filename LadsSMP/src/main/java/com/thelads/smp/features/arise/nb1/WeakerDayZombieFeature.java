package com.thelads.smp.features.arise.nb1;

import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native clean-room implementation of Weaker Day Zombie.
 * Applies Weakness and Slowness debuffs to zombies exposed to direct daylight.
 */
public final class WeakerDayZombieFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-WeakerDayZombie");

    public static void init() {
        LOGGER.info("[Lads SMP] Weaker Day Zombie initialized");
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().weakerDayZombie.enabled;
    }

    public static void applyDaylightDebuff(Zombie zombie) {
        if (!isEnabled() || zombie.level().isClientSide()) {
            return;
        }

        boolean burns = Boolean.TRUE.equals(zombie.level().environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, zombie.blockPosition()));
        if (burns && zombie.level().isBrightOutside() && zombie.level().canSeeSkyFromBelowWater(zombie.blockPosition())) {
            AriseNB1Config.WeakerDayZombieConfig cfg = AriseNB1Config.get().weakerDayZombie;
            zombie.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, cfg.weaknessDuration, cfg.weaknessAmplifier, false, false, true));
            zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cfg.slownessDuration, cfg.slownessAmplifier, false, false, true));
        }
    }
}
