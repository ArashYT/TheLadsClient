package com.thelads.smp.features.arise.nb2;

/**
 * Manages the client-side toggle for overhead player nametags (HideName / No Render Name Tags).
 */
public final class HideNameManager {
    private static volatile boolean hidingNametags = false;

    private HideNameManager() {}

    public static boolean isHidingNametags() {
        return NB2Config.get().hideName && hidingNametags;
    }

    public static void setHidingNametags(boolean hide) {
        hidingNametags = hide;
    }

    public static boolean toggle() {
        hidingNametags = !hidingNametags;
        return hidingNametags;
    }
}
