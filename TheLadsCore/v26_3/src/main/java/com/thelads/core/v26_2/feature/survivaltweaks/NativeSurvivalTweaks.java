package com.thelads.core.v26_2.feature.survivaltweaks;

import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v26_2.mixin.SurvivalFireBlockInvoker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.Blocks;

/**
 * Registers SurvivalTweaksModule and applies block flammability tweaks.
 */
public final class NativeSurvivalTweaks {
    private static final boolean EXTERNAL_LOADED = FabricLoader.getInstance().isModLoaded("misctweaks");

    private NativeSurvivalTweaks() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(SurvivalTweaksModule.NAME);
        if (!EXTERNAL_LOADED) {
            SurvivalTweaksModule mod = SurvivalTweaksModule.get();
            if (mod == null || mod.isFlammableCobwebsEnabled()) {
                try {
                    ((SurvivalFireBlockInvoker) Blocks.FIRE).lads$invokeSetFlammable(Blocks.COBWEB, 600, 60);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static boolean active() {
        return !EXTERNAL_LOADED && SurvivalTweaksModule.get() != null && SurvivalTweaksModule.get().isEnabled();
    }

    public static boolean isBoat360ViewEnabled() {
        return !FabricLoader.getInstance().isModLoaded("boatview360")
                && SurvivalTweaksModule.get() != null
                && SurvivalTweaksModule.get().isBoat360ViewEnabled();
    }

    public static boolean isLongerChatHistoryEnabled() {
        return !FabricLoader.getInstance().isModLoaded("longerchathistory")
                && SurvivalTweaksModule.get() != null
                && SurvivalTweaksModule.get().isLongerChatHistoryEnabled();
    }

    public static boolean isFastIpPingEnabled() {
        return !FabricLoader.getInstance().isModLoaded("fastipping")
                && SurvivalTweaksModule.get() != null
                && SurvivalTweaksModule.get().isFastIpPingEnabled();
    }

    public static boolean isKeepBarsWhileRidingEnabled() {
        return !FabricLoader.getInstance().isModLoaded("leavemybarsalone")
                && SurvivalTweaksModule.get() != null
                && SurvivalTweaksModule.get().isKeepBarsWhileRidingEnabled();
    }
}
