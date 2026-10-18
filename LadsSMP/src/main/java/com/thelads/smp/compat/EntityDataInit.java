package com.thelads.smp.compat;

import com.thelads.smp.LadsSMPMod;
import net.fabricmc.loader.api.FabricLoader;

/**
 * More Mob Variants 1.4.0 defines its chicken variant data id in a holder class that first loads inside the first
 * Chicken's defineSynchedData, i.e. after that chicken's SynchedEntityData builder was sized: the first chicken of every
 * session fails ("Data value id is too big with 26! (Max is 25)"), which is caught for natural spawns but crashes the
 * server for a chicken jockey or a spawn egg. Initializing Chicken (all its own ids) and then the holder at startup gives
 * the variant id a slot before any chicken exists.
 */
public final class EntityDataInit {
    private EntityDataInit() {}

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("moremobvariants")) return;
        ClassLoader cl = EntityDataInit.class.getClassLoader();
        try {
            Class.forName("net.minecraft.world.entity.animal.chicken.Chicken", true, cl);
            Class.forName("com.github.nyuppo.util.MMVTrackedData", true, cl);
        } catch (ClassNotFoundException | LinkageError e) {
            LadsSMPMod.LOGGER.warn("[Lads SMP] More Mob Variants chicken data id not pre-registered: {}", e.toString());
        }
    }
}
