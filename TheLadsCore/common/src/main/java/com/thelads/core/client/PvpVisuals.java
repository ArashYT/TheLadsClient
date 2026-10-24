package com.thelads.core.client;

import com.thelads.core.config.ColorOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.BlockOverlayModule;
import com.thelads.core.modules.DamageTintModule;
import com.thelads.core.modules.GlintColorizerModule;
import com.thelads.core.modules.HitColorModule;
import com.thelads.core.modules.HitboxesModule;
import com.thelads.core.modules.MountOpacityModule;

/**
 * The PvP visual modules (Block Overlay, Hit Color, Damage Tint, Glint Colorizer, Hide Mob Deaths, Mount Opacity, Hitboxes). Each
 * adapter calls {@link #tick} once a client tick; its render hooks then read the values the modules cached there, so no hook looks up
 * a module or an option per frame, block or entity.
 */
public final class PvpVisuals {
    public static final String HIDE_MOB_DEATHS = "Hide Mob Deaths";
    /** The modules every adapter builds in (ModuleSupport.registerBuiltIn). */
    public static final String[] MODULES = {BlockOverlayModule.NAME, HitColorModule.NAME, DamageTintModule.NAME, GlintColorizerModule.NAME,
        HIDE_MOB_DEATHS, MountOpacityModule.NAME, HitboxesModule.NAME};
    public static final BlockOverlayModule BLOCK_OVERLAY = get(BlockOverlayModule.NAME);
    public static final HitColorModule HIT_COLOR = get(HitColorModule.NAME);
    public static final DamageTintModule DAMAGE_TINT = get(DamageTintModule.NAME);
    public static final GlintColorizerModule GLINT = get(GlintColorizerModule.NAME);
    public static final MountOpacityModule MOUNT_OPACITY = get(MountOpacityModule.NAME);
    public static final HitboxesModule HITBOXES = get(HitboxesModule.NAME);
    private static final Module HIDE = get(HIDE_MOB_DEATHS);
    /** Hide Mob Deaths: a dying mob (not a player) is not drawn, so it vanishes at once instead of tipping over red. */
    public static boolean hideMobDeaths;

    private PvpVisuals() {}

    @SuppressWarnings("unchecked")
    private static <T extends Module> T get(String name) {
        return (T) ModuleManager.getInstance().getModule(name);
    }

    /** Once a client tick, on the client thread: the modules cache their state and options for the render hooks. */
    public static void tick() {
        BLOCK_OVERLAY.refresh();
        HIT_COLOR.refresh();
        DAMAGE_TINT.refresh();
        GLINT.refresh();
        MOUNT_OPACITY.refresh();
        HITBOXES.refresh();
        hideMobDeaths = HIDE.isEnabled();
    }

    /** A colour setting's ARGB: its own, or with "Global" the HUD colour in its own alpha. */
    public static int argb(ColorOption option) {
        return argb(option.isUseGlobal(), option.getColor(), HudSettings.getInstance().getGlobalColor());
    }

    static int argb(boolean global, int own, int globalColor) {
        return global ? own & 0xFF000000 | globalColor & 0xFFFFFF : own;
    }
}
