package com.thelads.core.v1_20_1.game.survival;

import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import com.thelads.core.v1_20_1.game.survival.mixin.SurvivalFireBlockInvoker1201;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SurvivalTweaks on 1.20.1 (the 26.3 NativeSurvivalTweaks): registers the module as built in, applies the cobweb flammability and
 * answers each mixin with "may I act?". The mixins live in theladscore.v1_20_1.survival.mixins.json. Each tweak stands down for the
 * standalone mod it replaces (Misc Tweaks, Boat View 360, Longer Chat History, Fast IP Ping, Leave My Bars Alone).
 */
public final class NativeSurvivalTweaks1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1");
    /** QA only (LADS_QA_PARITY=1): how often the mixins asked {@link #active()}, to show they ran. */
    public static final boolean QA = System.getenv("LADS_QA_PARITY") != null;
    public static final AtomicInteger GATE_CALLS = new AtomicInteger();

    private NativeSurvivalTweaks1201() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(SurvivalTweaksModule.NAME);
        if (ModPresence1201.onForge()) {
            ModuleSupport.registerBuiltInLimited(SurvivalTweaksModule.NAME, "Keep Bars While Riding is not available through Forge (Zero SMP): Forge draws its own HUD.");
            if (SurvivalTweaksModule.get() != null) SurvivalTweaksModule.get().hideHere("Keep Bars While Riding");
        }
        if (!ModPresence1201.loaded("misctweaks")) {
            SurvivalTweaksModule mod = SurvivalTweaksModule.get();
            if (mod == null || mod.isFlammableCobwebsEnabled()) {
                try {
                    ((SurvivalFireBlockInvoker1201) Blocks.FIRE).lads$invokeSetFlammable(Blocks.COBWEB, 600, 60);
                } catch (Throwable failure) {
                    LOGGER.warn("Lads SurvivalTweaks: cobwebs could not be made flammable", failure);
                }
            }
        }
    }

    /** The module is there and on, and Misc Tweaks is not installed. */
    public static boolean active() {
        if (QA) GATE_CALLS.incrementAndGet();
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        return mod != null && mod.isEnabled() && !ModPresence1201.loaded("misctweaks");
    }

    public static boolean isBoat360ViewEnabled() {
        return !ModPresence1201.loaded("boatview360") && SurvivalTweaksModule.get() != null && SurvivalTweaksModule.get().isBoat360ViewEnabled();
    }

    public static boolean isLongerChatHistoryEnabled() {
        return !ModPresence1201.loaded("longerchathistory") && SurvivalTweaksModule.get() != null && SurvivalTweaksModule.get().isLongerChatHistoryEnabled();
    }

    public static boolean isFastIpPingEnabled() {
        return !ModPresence1201.loaded("fastipping") && SurvivalTweaksModule.get() != null && SurvivalTweaksModule.get().isFastIpPingEnabled();
    }

    public static boolean isKeepBarsWhileRidingEnabled() {
        return !ModPresence1201.loaded("leavemybarsalone") && SurvivalTweaksModule.get() != null && SurvivalTweaksModule.get().isKeepBarsWhileRidingEnabled();
    }
}
