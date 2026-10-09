package com.thelads.core.v1_20_1.game.misc;

import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.MouseTweaksModule;
import org.slf4j.LoggerFactory;

/**
 * Item Physics, Dynamic Lights, Mouse Tweaks, far block entities and item model gaps. Registered by Gameplay1201; mixins in
 * theladscore.v1_20_1.misc.mixins.json. The client entrypoint registered the module names as "Built in" for everything first; what
 * this game cannot do or hands to an installed mod is corrected here.
 */
public final class Misc1201 {
    private Misc1201() {}

    public static void register() {
        NativeItemPhysics1201.initialize();
        NativeDynamicLights1201.initialize();
        registerMouseTweaks();
        MiscQa1201.register();
        // Far block entities and the item model gaps are plain mixins with nothing to start: FarBlockEntities stays "Built in".
    }

    /** Lads Mouse Tweaks replaces the mod; with the mod installed (Forge jar on Zero SMP included) it keeps the job. */
    private static void registerMouseTweaks() {
        if (NativeMouseTweaks1201.externalMod()) {
            ModuleSupport.registerExternal(MouseTweaksModule.NAME, "Mouse Tweaks", "mousetweaks", true);
            LoggerFactory.getLogger("TheLadsCore").info("Lads Mouse Tweaks: the Mouse Tweaks mod is installed and keeps the job");
        } else {
            ModuleSupport.registerBuiltIn(MouseTweaksModule.NAME);
        }
    }
}
