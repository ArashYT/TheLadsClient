package com.thelads.core.v1_21_1.embedded.lazyai;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import org.slf4j.LoggerFactory;

/**
 * Lazy AI (independent Lads implementation, see NOTICE.txt): on the integrated server a mob far from every player
 * re-evaluates its goals and targets, clears its line-of-sight cache, runs brain sensors and recomputes paths less
 * often, scaled by that distance. Within {@link #VANILLA_RADIUS} blocks of any player, and for anything busy or
 * special (bosses, villagers, raiders, tamed, named, leashed, riding or ridden, persistent, fighting, hurt, burning),
 * vanilla timing is kept.
 */
public final class LazyAi {
    public static final int VANILLA_RADIUS = 32;
    /** Vanilla evaluates goals every second tick; lazy intervals are multiples of it so they only drop ticks. */
    public static final int VANILLA_INTERVAL = 2;
    private static final boolean QA = Boolean.getBoolean("thelads.verifyAutoWorld");
    private static final AtomicLong SKIPPED_GOALS = new AtomicLong(), SKIPPED_SENSING = new AtomicLong(), SLOWED_SENSORS = new AtomicLong();
    private static volatile long nextReport;

    private LazyAi() {}

    /** Distance band interval, recomputed every AI tick: 2 (vanilla) below 32 blocks from the nearest player, then 4, 6, 8. */
    public static int distanceInterval(Mob mob) {
        if (exemptType(mob)) return VANILLA_INTERVAL;
        double nearest = Double.MAX_VALUE;
        for (Player player : mob.level().players()) nearest = Math.min(nearest, player.distanceToSqr(mob));
        if (nearest < sq(VANILLA_RADIUS)) return VANILLA_INTERVAL;
        if (nearest < sq(64)) return 4;
        if (nearest < sq(96)) return 6;
        return 8;
    }

    /** The interval that applies this tick: vanilla while the mob is busy, whatever its distance. */
    public static int interval(Mob mob) {
        int interval = ((LazyMob) mob).lads$lazyInterval();
        return interval > VANILLA_INTERVAL && busy(mob) ? VANILLA_INTERVAL : interval;
    }

    /** True when a lazy mob skips this tick's full goal/target evaluation (vanilla's own odd-tick path runs instead). */
    public static boolean skipsEvaluation(Mob mob) {
        int interval = interval(mob);
        return interval > VANILLA_INTERVAL && mob.tickCount > 1 && (mob.tickCount + mob.getId()) % interval != 0;
    }

    /** Sensor scan and path recompute slow-down: x1 near players, up to x4 at the farthest band. */
    public static int multiplier(Mob mob) {
        return Math.max(1, interval(mob) / VANILLA_INTERVAL);
    }

    public static void countGoals() { if (QA) SKIPPED_GOALS.incrementAndGet(); }
    public static void countSensing() { if (QA) SKIPPED_SENSING.incrementAndGet(); }
    public static void countSensor() { if (QA) SLOWED_SENSORS.incrementAndGet(); }

    /** QA only (-Dthelads.verifyAutoWorld): one counter line per minute of game time. */
    public static void report(long gameTime) {
        if (!QA || gameTime < nextReport) return;
        boolean first = nextReport == 0;
        nextReport = gameTime + 1200;
        if (!first) LoggerFactory.getLogger("LazyAI").info("Lazy AI: skipped {} goal evaluations, {} sensing clears, slowed {} sensor scans",
            SKIPPED_GOALS.get(), SKIPPED_SENSING.get(), SLOWED_SENSORS.get());
    }

    private static boolean exemptType(Mob mob) {
        return mob instanceof EnderDragon || mob instanceof WitherBoss || mob instanceof Warden || mob instanceof ElderGuardian
            || mob instanceof AbstractVillager || mob instanceof Raider raider && raider.hasActiveRaid()
            || mob instanceof TamableAnimal tamable && tamable.isTame() || mob instanceof AbstractHorse horse && horse.isTamed()
            || mob.hasCustomName() || mob.isPersistenceRequired();
    }

    private static boolean busy(Mob mob) {
        return mob.getTarget() != null || mob.isAggressive() || mob.hurtTime > 0 || mob.getLastHurtByMob() != null
            || mob.isLeashed() || mob.isPassenger() || mob.isVehicle() || mob.isOnFire();
    }

    private static double sq(double blocks) { return blocks * blocks; }
}
