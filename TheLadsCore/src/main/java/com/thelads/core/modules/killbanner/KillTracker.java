package com.thelads.core.modules.killbanner;

import net.minecraft.world.entity.Entity;
import com.thelads.core.config.ModuleManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KillTracker {
    private static final Map<Integer, Long> lastAttackTimes = new ConcurrentHashMap<>();

    public static void setLastAttackedEntity(Entity entity) {
        if (!ModuleManager.getInstance().getModule("KillBanner").isEnabled()) return;
        if (entity != null) {
            lastAttackTimes.put(entity.getId(), System.currentTimeMillis());
        }
    }

    public static void onEntityDeath(Entity entity) {
        if (entity != null) {
            Long lastAttackTime = lastAttackTimes.remove(entity.getId());
            if (lastAttackTime != null && (System.currentTimeMillis() - lastAttackTime) < 5000) {
                KillBannerRenderer.triggerKill();
            }
        }
    }
}
