package com.thelads.core.v1_20_1.feature;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final Set<Integer> landed = new HashSet<>();

    private KillBannerArrows1201() {}

    static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        Player me = minecraft.player;
        if (level == null || me == null) {
            flying.clear();
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
            Vec3 before = flying.put(id, at);
            if (motion.lengthSqr() < 1e-6) continue;
            Vec3 from = before != null ? before : at, to = at.add(motion.scale(1.5));
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
            }
        }
        flying.keySet().retainAll(seen);
        landed.retainAll(seen);
    }
}
