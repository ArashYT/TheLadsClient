// Adapted from Controlling 26.2.4 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.platform;

import net.fabricmc.loader.api.FabricLoader;

public class FabricHelper {
    
    public static boolean isFabricLoaded() {
        
        return FabricLoader.getInstance().isModLoaded("fabric-api");
    }
    
}
