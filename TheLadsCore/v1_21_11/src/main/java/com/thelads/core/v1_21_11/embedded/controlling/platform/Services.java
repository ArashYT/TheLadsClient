// Adapted from Controlling 29.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.controlling.platform;

// The Lads: the Fabric implementations directly, instead of ServiceLoader lookups through META-INF/services.
public class Services {
    
    public static final IEventHelper EVENT = new FabricEventHandler();
    public static final IPlatformHelper PLATFORM = new FabricPlatformHelper();
    
}
