package com.thelads.core.client;

/**
 * Bobbing Disconnect: Removes dizzying camera tilt from view bobbing while keeping
 * first-person hand bobbing animations.
 */
public final class BobbingTweaks {
    private static volatile boolean disconnectEnabled;

    private BobbingTweaks() {}

    public static void setDisconnectEnabled(boolean enabled) {
        disconnectEnabled = enabled;
    }

    public static boolean isDisconnectEnabled() {
        return disconnectEnabled;
    }
}
