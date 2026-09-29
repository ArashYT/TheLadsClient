package com.thelads.core.v26_2.feature;

import com.thelads.core.modules.KillBannerModule;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import org.slf4j.LoggerFactory;

/** Exercises transformed server-packet handling with synthetic QA packets; never claims a real PvP kill. */
final class NativeKillBannerProbe {
    private NativeKillBannerProbe() {}

    static int run() {
        Minecraft minecraft = Minecraft.getInstance();
        KillBannerModule module = (KillBannerModule) NativeQualityOfLife.module("KillBanner");
        boolean enabledBefore = module.isEnabled(), soundBefore = module.sound.get();
        long modifiedBefore = module.getLastModified();
        Stat<?> stat = Stats.CUSTOM.get(Stats.PLAYER_KILLS);
        int totalBefore = minecraft.player.getStats().getValue(stat);
        int passed = 0;
        boolean completed = false;
        try {
            module.setEnabled(true);
            module.sound.set(false);
            NativeKillBanner.reset();
            dispatch(40);
            require(NativeKillBanner.timeline().age(System.nanoTime()) < 0, "initial server statistics do not emit historical kills"); passed++;
            dispatch(41);
            require(NativeKillBanner.timeline().delta() == 1 && !NativeKillBanner.timeline().preview(), "transformed packet handler emits one player-kill delta"); passed++;
            dispatch(41);
            require(NativeKillBanner.timeline().sequence() == 1, "duplicate packet does not advance sequence"); passed++;
            dispatch(44);
            require(NativeKillBanner.timeline().delta() == 3 && NativeKillBanner.timeline().sequence() == 4, "batched player kills preserve exact delta"); passed++;
            module.setEnabled(false);
            dispatch(45);
            require(NativeKillBanner.timeline().age(System.nanoTime()) < 0, "disabled module clears and ignores packet events"); passed++;
            module.setEnabled(true);
            dispatch(46);
            require(NativeKillBanner.timeline().age(System.nanoTime()) < 0, "reenabling establishes a fresh baseline"); passed++;
            for (String texture : new String[] {"base_kill_banner", "reaver_kill_banner"}) {
                require(minecraft.getResourceManager().getResource(Identifier.fromNamespaceAndPath("theladscore", "textures/gui/" + texture + ".png")).isPresent(), texture + " resource loads"); passed++;
            }
            for (int index = 1; index <= 5; index++) {
                Identifier sound = Identifier.fromNamespaceAndPath("theladscore", "reaver_kill_" + index);
                require(minecraft.getSoundManager().getSoundEvent(sound) != null, "Reaver sound event " + index + " registered"); passed++;
                require(minecraft.getResourceManager().getResource(Identifier.fromNamespaceAndPath("theladscore", "sounds/killbanner/reaver-kill-" + index + ".ogg")).isPresent(), "Reaver sound sample " + index + " available"); passed++;
            }
            LoggerFactory.getLogger("TheLadsCore").info("Lads kill banner probe END: {} passed, 0 failed (synthetic server-stat packets; no real PvP kill claimed)", passed);
            completed = true;
            return passed;
        } finally {
            module.setEnabled(false);
            dispatch(totalBefore);
            NativeKillBanner.reset();
            module.sound.set(soundBefore);
            module.setEnabled(enabledBefore);
            module.setLastModified(modifiedBefore);
            if (completed) NativeKillBannerPreview.schedule();
        }
    }

    private static void dispatch(int value) {
        var stats = new Object2IntOpenHashMap<Stat<?>>();
        stats.put(Stats.CUSTOM.get(Stats.PLAYER_KILLS), value);
        Minecraft.getInstance().getConnection().handleAwardStats(new ClientboundAwardStatsPacket(stats));
    }
    private static void require(boolean value, String name) {
        if (!value) throw new IllegalStateException(name);
    }
}
