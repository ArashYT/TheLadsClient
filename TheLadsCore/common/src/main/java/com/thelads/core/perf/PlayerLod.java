package com.thelads.core.perf;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.PlayerLodModule;
import org.slf4j.LoggerFactory;

/**
 * Lads remake of Xenon's distance LOD for other players: far players skip their outer skin layer, armor trims, cape and elytra,
 * held and head items, and optionally name tags. Visual only. Off for the session with {@code -Dlads.playerlod=false}, when the
 * real Xenon is installed; the outer-layer cut also stands down when 3D Skin Layers (which has its own LOD) is installed.
 */
public final class PlayerLod {
    public static final int OUTER = 1, TRIM = 2, CAPE = 4, ITEMS = 8, NAME = 16;
    /** Never touch anything closer than this, whatever the sliders say. */
    private static final double MIN = 16;

    private static String reason;
    private static boolean decided, skinLayers, logged;
    private static PlayerLodModule module;
    private static com.thelads.core.modules.ZoomModule zoom;
    /** Set by the version code each frame the local player looks through a spyglass. */
    public static boolean scoping;

    private PlayerLod() {}

    /** Why Player LOD is off this session (kill switch or Xenon), or null. */
    public static synchronized String standDownReason() {
        if (!decided) {
            reason = BuiltInRender.decide("playerlod", System.getProperty("lads.playerlod"), "xenon");
            skinLayers = BuiltInRender.modPresent("skinlayers3d");
            decided = true;
            if (reason != null) LoggerFactory.getLogger("TheLadsCore").info("[Lads] Player LOD off ({})", reason);
        }
        return reason;
    }

    /** Cut-off bits for a player {@code distSq} squared blocks from the camera; 0 when nothing applies. Render thread. */
    public static int bits(double distSq) {
        if (distSq < MIN * MIN || standDownReason() != null) return 0;
        PlayerLodModule m = module;
        if (m == null) {
            m = module = (PlayerLodModule) ModuleManager.getInstance().getModule(PlayerLodModule.NAME);
            if (m == null) return 0;
        }
        if (!m.isEnabled() || zoomed()) return 0;
        int bits = 0;
        if (!skinLayers && m.outer.get() && far(distSq, m.outerDistance.getValue())) bits |= OUTER;
        if (m.trims.get() && far(distSq, m.trimsDistance.getValue())) bits |= TRIM;
        if (m.capes.get() && far(distSq, m.capesDistance.getValue())) bits |= CAPE;
        if (m.items.get() && far(distSq, m.itemsDistance.getValue())) bits |= ITEMS;
        if (m.names.get() && far(distSq, m.namesDistance.getValue())) bits |= NAME;
        if (bits != 0 && !logged) {
            logged = true;
            LoggerFactory.getLogger("TheLadsCore").info("[Lads] Player LOD active: first far player cut (bits {}, {} blocks)", bits, (int) Math.sqrt(distSq));
        }
        return bits;
    }

    /** Zoom (Lads zoom, spyglass on 1.20.1/26.3 via {@link #scoping}) magnifies far players, so the cuts would show: nothing is cut then. */
    private static boolean zoomed() {
        if (scoping) return true;
        if (zoom == null) zoom = (com.thelads.core.modules.ZoomModule) ModuleManager.getInstance().getModule("Zoom");
        return zoom != null && zoom.sensitivity() < 0.999f;
    }

    private static boolean far(double distSq, double blocks) {
        double d = Math.max(MIN, blocks);
        return distSq > d * d;
    }
}
