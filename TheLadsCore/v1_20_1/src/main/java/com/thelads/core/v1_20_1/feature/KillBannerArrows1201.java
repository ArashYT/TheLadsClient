package com.thelads.core.v1_20_1.feature;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.thelads.core.modules.ComboSoundsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class KillBannerArrows1201 {
    private static final Map<Integer, Vec3> flying = new HashMap<>();
    /** Each arrow in flight by entity id: how it moved last tick. */
    private static final Map<Integer, Vec3> moving = new HashMap<>();
    private static final Set<Integer> landed = new HashSet<>();

    private KillBannerArrows1201() {}

    static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        Player me = minecraft.player;
        if (level == null || me == null) {
            flying.clear();
            moving.clear();
            landed.clear();
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof AbstractArrow arrow) || arrow.getOwner() != me) continue;
            int id = arrow.getId();
            seen.add(id);
            if (landed.contains(id)) continue;
            Vec3 at = arrow.position(), motion = arrow.getDeltaMovement();
            Vec3 before = flying.put(id, at), was = moving.put(id, motion);
            if (motion.lengthSqr() < 1e-6) continue;
            Vec3 from = before != null ? before : at, to = at.add(motion.scale(1.5));
            // Turned back: the client's own copy bounced off what it hit this tick (only the server hurts), on last tick's path.
            if (before != null && was != null && was.dot(motion) < 0) to = before.add(was.scale(1.5));
            LivingEntity victim = null;
            Vec3 where = null;
            double nearest = Double.MAX_VALUE;
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(.5), e -> e != me && e.isAlive())) {
                Optional<Vec3> hit = candidate.getBoundingBox().inflate(.3).clip(from, to);
                if (hit.isEmpty()) continue;
                double d = from.distanceToSqr(hit.get());
                if (d < nearest) {
                    nearest = d;
                    victim = candidate;
                    where = hit.get();
                }
            }
            if (victim != null) {
                landed.add(id);
                NativeKillBanner1201.arrowHit(victim, from, where);
                ComboSoundsModule.onArrowCrossed(victim.getId(), victim instanceof Player, NativeKillBanner1201.headHit(victim, from, where), me.distanceTo(victim));
            }
        }
        flying.keySet().retainAll(seen);
        moving.keySet().retainAll(seen);
        landed.retainAll(seen);
    }

    /**
     * The server just hurt {@code victim}: the client's copies of your arrows run a tick or two behind the server's, so the arrow
     * that hit may not have crossed it here yet and is about to be removed. The first of your arrows whose path (followed for a few
     * ticks, falling as arrows do; for one that bounced off on this client, last tick's path) meets its box is where it hit.
     */
    static void reach(Minecraft minecraft, Entity victim) {
        ClientLevel level = minecraft.level;
        Player me = minecraft.player;
        if (level == null || me == null || !(victim instanceof LivingEntity) || victim == me) return;
        AABB box = victim.getBoundingBox().inflate(.3);
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof AbstractArrow arrow) || arrow.getOwner() != me || landed.contains(arrow.getId())) continue;
            Vec3 at = arrow.position(), motion = arrow.getDeltaMovement(), was = moving.get(arrow.getId());
            if (was != null && was.dot(motion) < 0 && flying.containsKey(arrow.getId())) {
                at = flying.get(arrow.getId());
                motion = was;
            }
            for (int tick = 0; tick < 6 && motion.lengthSqr() > 1e-6; tick++) {
                Vec3 next = at.add(motion);
                Optional<Vec3> hit = box.contains(at) ? Optional.of(at) : box.clip(at, next);
                if (hit.isPresent()) {
                    landed.add(arrow.getId());
                    NativeKillBanner1201.arrowHit(victim, at, hit.get());
                    ComboSoundsModule.onArrowCrossed(victim.getId(), victim instanceof Player, NativeKillBanner1201.headHit(victim, at, hit.get()), me.distanceTo(victim));
                    return;
                }
                at = next;
                motion = motion.scale(.99).add(0, -.05, 0);
            }
        }
    }
}
