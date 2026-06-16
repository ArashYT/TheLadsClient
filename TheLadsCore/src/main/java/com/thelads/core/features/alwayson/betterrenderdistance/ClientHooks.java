/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.features.alwayson.betterrenderdistance;

import com.thelads.core.features.alwayson.betterrenderdistance.config.BRDConfig;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;

public final class ClientHooks {
    private static final AtomicBoolean RELOAD_QUEUED = new AtomicBoolean(false);

    private ClientHooks() {
    }

    public static void onConfigApplied() {
        BRDConfig.save();
        if (!RELOAD_QUEUED.compareAndSet(false, true)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            RELOAD_QUEUED.set(false);
            return;
        }
        mc.execute(() -> {
            try {
                if (mc.levelRenderer != null) {
                    mc.levelRenderer.allChanged();
                }
            }
            finally {
                RELOAD_QUEUED.set(false);
            }
        });
    }
}

