package com.thelads.core.v26_2.feature;

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
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * KillBanner headshots from arrows: the local player's arrows in flight, watched each client tick; where one's path
 * (from where it was to where it is going) crosses a living entity's box is where it hit, in the head or not
 * (NativeKillBanner.arrowHit). The server decides the real hit; this follows the same arrow a
 * tick or so behind, close enough to tell the head from the body.
 */
final class KillBannerArrows {
    /** Each arrow in flight by entity id: where it was last tick. */
    private static final Map<Integer, Vec3> flying = new HashMap<>();
    /** Each arrow in flight by entity id: how it moved last tick. */
    private static final Map<Integer, Vec3> moving = new HashMap<>();
    /** Arrows that already hit someone: one hit an arrow. */
    private static final Set<Integer> landed = new HashSet<>();

    private KillBannerArrows() {}

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
            if (motion.lengthSqr() < 1e-6) continue; // stuck in the ground, or not moving yet
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
                NativeKillBanner.arrowHit(victim, from, where);
                ComboSoundsModule.onArrowCrossed(victim.getId(), victim instanceof Player, NativeKillBanner.headHit(victim, from, where), me.distanceTo(victim));
            }
        }
        flying.keySet().retainAll(seen);
        moving.keySet().retainAll(seen);
        landed.retainAll(seen);
    }
}
