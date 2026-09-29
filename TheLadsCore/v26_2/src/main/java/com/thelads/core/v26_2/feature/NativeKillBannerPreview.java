package com.thelads.core.v26_2.feature;

import com.thelads.core.modules.KillBannerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.slf4j.LoggerFactory;

/** Only scheduled by the opt-in QA probe, for a bounded, explicitly labeled visual preview. */
final class NativeKillBannerPreview {
    private static boolean pending, active, enabledBefore, soundBefore;
    private static int styleBefore;
    private static double durationBefore;
    private static long due, started, lastTrigger;
    private static long modifiedBefore;
    private static ClientPacketListener connection;
    private NativeKillBannerPreview() {}

    static void schedule() {
        if (!Boolean.getBoolean("thelads.verifyKillBannerPreview")) return;
        pending = true;
        due = System.nanoTime() + 1_000_000_000L;
    }
    static boolean active() { return active; }

    static boolean tick() {
        if (!pending && !active) return false;
        Minecraft minecraft = Minecraft.getInstance();
        KillBannerModule module = (KillBannerModule) NativeQualityOfLife.module("KillBanner");
        long now = System.nanoTime();
        if (pending) {
            if (now < due || minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) return false;
            pending = false;
            enabledBefore = module.isEnabled();
            modifiedBefore = module.getLastModified();
            soundBefore = module.sound.get();
            styleBefore = module.bannerStyle.getIndex();
            durationBefore = module.duration.getValue();
            module.setEnabled(true);
            module.sound.set(false);
            module.bannerStyle.setIndex(1);
            module.duration.setValue(5);
            connection = minecraft.getConnection();
            active = true;
            started = now;
            lastTrigger = now - 4_000_000_000L;
            LoggerFactory.getLogger("TheLadsCore").info("Lads kill banner visual PREVIEW: 20 seconds, no kill statistic changed");
        }
        if (minecraft.level == null || minecraft.getConnection() != connection || now - started >= 20_000_000_000L) {
            active = false;
            connection = null;
            module.setEnabled(enabledBefore);
            module.sound.set(soundBefore);
            module.bannerStyle.setIndex(styleBefore);
            module.duration.setValue(durationBefore);
            module.setLastModified(modifiedBefore);
            NativeKillBanner.reset();
            return false;
        }
        if (now - lastTrigger >= 4_000_000_000L) {
            lastTrigger = now;
            NativeKillBanner.trigger(3, true);
        }
        return true;
    }
}
