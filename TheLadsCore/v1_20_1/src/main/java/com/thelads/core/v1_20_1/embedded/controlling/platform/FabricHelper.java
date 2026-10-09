// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_20_1.embedded.controlling.platform;

public class FabricHelper {

    /** Fabric API is a hard dependency of Core (also as Forgified Fabric API under Connector), so its events always exist. */
    public static boolean isFabricLoaded() {

        return true;
    }

}
