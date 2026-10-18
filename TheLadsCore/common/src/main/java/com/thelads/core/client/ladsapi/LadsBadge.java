package com.thelads.core.client.ladsapi;

import java.util.UUID;

/**
 * The Lads Client badge (the anime girl logo) next to Lads players' names. FROZEN INTERFACE (phase 0): lane BADGE-A implements the
 * module ("Lads Badge": Tab / Nametag / Show mine) and the texture; lanes BADGE-B (1.8.9 + 1.20.1) and BADGE-C (26.3) draw it.
 * Asked per player per frame: no allocation.
 */
public final class LadsBadge {
    /** 64x64 PNG made from icons/window_256.png. Namespace + path for each version's texture loader. */
    public static final String TEXTURE_NAMESPACE = "theladscore", TEXTURE_PATH = "textures/gui/lads_badge.png";
    public static final int TEXTURE_SIZE = 64;
    /** Drawn size in GUI pixels: tab row (8) and in front of the nametag text (9 = font height). */
    public static final int TAB_SIZE = 8, NAMETAG_SIZE = 9;
    /** Gap between the badge and the name, GUI pixels. */
    public static final int GAP = 1;

    private LadsBadge() {}

    /** Draw the badge in front of this player's tab-list name. */
    public static boolean showInTab(UUID player) {
        return false; // lane BADGE-A: module on, Tab option on, LadsPresence.isLads(player), own-badge rule
    }

    /** Draw the badge in front of this player's nametag. */
    public static boolean showOnNametag(UUID player) {
        return false; // lane BADGE-A
    }
}
