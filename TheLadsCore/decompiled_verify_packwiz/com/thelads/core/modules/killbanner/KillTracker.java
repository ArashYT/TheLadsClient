/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 */
package com.thelads.core.modules.killbanner;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.killbanner.KillBannerRenderer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;

public class KillTracker {
    private static final Map<Integer, Long> lastAttackTimes = new ConcurrentHashMap<Integer, Long>();

    public static void setLastAttackedEntity(Entity entity) {
        if (!ModuleManager.getInstance().getModule("KillBanner").isEnabled()) {
            return;
        }
        if (entity != null) {
            lastAttackTimes.put(entity.getId(), System.currentTimeMillis());
        }
    }

    public static void onEntityDeath(Entity entity) {
        Long lastAttackTime;
        if (entity != null && (lastAttackTime = lastAttackTimes.remove(entity.getId())) != null && System.currentTimeMillis() - lastAttackTime < 5000L) {
            KillBannerRenderer.triggerKill();
        }
    }
}

