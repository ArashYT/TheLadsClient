package com.thelads.core.client.ladsapi;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.LadsBadgeModule;
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

    private static volatile LadsBadgeModule cachedModule;
    private static volatile UUID cachedSelfUuid;
    private static volatile LadsGameBridge lastBridge;
    private static volatile int lastTick = Integer.MIN_VALUE;

    private LadsBadge() {}

    private static LadsBadgeModule module() {
        LadsBadgeModule m = cachedModule;
        if (m == null) {
            Module mod = ModuleManager.getInstance().getModule(LadsBadgeModule.NAME);
            if (mod instanceof LadsBadgeModule badgeMod) {
                m = badgeMod;
                cachedModule = m;
            }
        }
        return m;
    }

    private static UUID selfUuid() {
        LadsGameBridge bridge = LadsGameBridge.get();
        int tick = bridge.getClientTicks();
        if (tick != lastTick || bridge != lastBridge) {
            lastTick = tick;
            lastBridge = bridge;
            LadsGameBridge.Session s = bridge.session();
            cachedSelfUuid = s != null ? s.uuid() : null;
        }
        return cachedSelfUuid;
    }

    /** Reset cached references (for unit tests). */
    static void clearCacheForTest() {
        cachedModule = null;
        cachedSelfUuid = null;
        lastBridge = null;
        lastTick = Integer.MIN_VALUE;
    }

    /** Draw the badge in front of this player's tab-list name. */
    public static boolean showInTab(UUID player) {
        if (player == null) return false;
        LadsBadgeModule m = module();
        if (m == null || !m.isEnabled() || !m.tab.get()) return false;
        UUID me = selfUuid();
        if (me != null && me.equals(player)) {
            return m.showMine.get();
        }
        return LadsPresence.isLads(player);
    }

    /** Draw the badge in front of this player's nametag. */
    public static boolean showOnNametag(UUID player) {
        if (player == null) return false;
        LadsBadgeModule m = module();
        if (m == null || !m.isEnabled() || !m.nametag.get()) return false;
        UUID me = selfUuid();
        if (me != null && me.equals(player)) {
            return m.showMine.get();
        }
        return LadsPresence.isLads(player);
    }
}
