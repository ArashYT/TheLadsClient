package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.BlockOverlayModule;
import com.thelads.core.modules.DamageTintModule;
import com.thelads.core.modules.GlintColorizerModule;
import com.thelads.core.modules.HitColorModule;
import com.thelads.core.modules.HitboxesModule;
import com.thelads.core.modules.ItemDespawnModule;
import com.thelads.core.modules.LootBeamsModule;
import com.thelads.core.modules.MobOverlayModule;
import com.thelads.core.modules.MobSizeModule;
import com.thelads.core.modules.OofModule;
import com.thelads.core.modules.UhcOverlayModule;
import com.thelads.core.modules.MountOpacityModule;
import com.thelads.core.modules.OldAnimationsModule;

/**
 * The PvP visual modules: Block Overlay, Hit Color, Damage Tint, Glint Colorizer, Hide Mob Deaths, Mount Opacity and Hitboxes; Render
 * Tweaks, Overlays, Totem Animation, Dark Mode and the Nametags extras. Each adapter calls {@link #tick} once a client tick; its render
 * hooks then read the values cached there, so no hook looks up a module or an option per frame, block or entity. Nametag Health shows
 * more than vanilla does (other players' health), so HypixelSafetyManager gates it: the module reads as off on servers that ban it or
 * whose rules are unknown. Water fog is Fog Customizer's Clear Water. Rows 31-38 add Oof Mod, Light Overlay, Loot Beams, Mob Overlay,
 * Mob Size, Item Despawn and UHC Overlay (their modules cache their own state here); Light Overlay, Loot Beams, Mob Overlay and Mob
 * Size are gated the same way.
 */
public final class PvpVisuals {
    public static final String HIDE_MOB_DEATHS = "Hide Mob Deaths";
    public static final String RENDER_TWEAKS = "Render Tweaks", SHADOWS = "Entity Shadows", HIDE_LIGHTNING = "Hide Lightning",
        HIDE_FISHING_LINE = "Hide Fishing Line", HIDE_ELDER_GUARDIAN = "Hide Elder Guardian Curse", BOBBING_DISCONNECT = "Bobbing Disconnect";
    public static final String OVERLAYS = "Overlays", FIRE_OPACITY = "Fire Opacity", FIRE_RESISTANCE = "Hide Fire with Fire Resistance",
        PUMPKIN_OPACITY = "Pumpkin Opacity", PORTAL_OPACITY = "Portal Opacity";
    public static final String TOTEM = "Totem Animation", HIDE_TOTEM = "Hide Animation", TOTEM_SCALE = "Totem Scale";
    public static final String DARK_MODE = "Dark Mode", DARKNESS = "Darkness";
    public static final String NAMETAG_HEALTH = "Nametag Health";
    /** The modules every adapter builds in (ModuleSupport.registerBuiltIn); Totem Animation only where totems exist. */
    public static final String[] MODULES = {BlockOverlayModule.NAME, HitColorModule.NAME, DamageTintModule.NAME, GlintColorizerModule.NAME,
        HIDE_MOB_DEATHS, MountOpacityModule.NAME, HitboxesModule.NAME, RENDER_TWEAKS, OVERLAYS, DARK_MODE, NAMETAG_HEALTH,
        OofModule.NAME, LightOverlay.NAME, LootBeamsModule.NAME, MobOverlayModule.NAME, MobSizeModule.NAME, ItemDespawnModule.NAME, UhcOverlayModule.NAME};
    public static final BlockOverlayModule BLOCK_OVERLAY = get(BlockOverlayModule.NAME);
    public static final HitColorModule HIT_COLOR = get(HitColorModule.NAME);
    public static final DamageTintModule DAMAGE_TINT = get(DamageTintModule.NAME);
    public static final GlintColorizerModule GLINT = get(GlintColorizerModule.NAME);
    public static final MountOpacityModule MOUNT_OPACITY = get(MountOpacityModule.NAME);
    public static final HitboxesModule HITBOXES = get(HitboxesModule.NAME);
    private static final Module HIDE = get(HIDE_MOB_DEATHS);
    /** Hide Mob Deaths: a dying mob (not a player) is not drawn, so it vanishes at once instead of tipping over red. */
    public static boolean hideMobDeaths;
    /** The dark gray (0x404040) vanilla draws container titles in, and the light gray Dark Mode draws them in instead. */
    static final int LABEL = 0x404040, LIGHT_LABEL = 0xE0E0E0;

    // Render Tweaks
    public static boolean hideShadows, hideLightning, hideFishingLine, hideElderGuardian, bobbingDisconnect;
    // Overlays: alpha factors (1 = vanilla)
    public static float fireOpacity = 1, pumpkinOpacity = 1, portalOpacity = 1;
    public static boolean hideFireWithResistance;
    // Totem Animation
    public static boolean hideTotem;
    public static float totemScale = 1;
    /** Dark Mode: the ARGB fill drawn over a container's background, 0 while off. */
    public static int darkFill;
    /** Nametags extras: tag scale, background alpha (0-255, -1 keeps the module's own), distance fade, ping, health, F1. */
    public static float nametagScale = 1;
    public static int nametagBackground = -1;
    public static boolean nametagFade, nametagPing, nametagHealth, nametagsInF1;
    /** 1.7 Animations' PvP extras (Constant swing speed, No re-equip on item changes): they apply on every version. */
    public static boolean constantSwing, noReequip, pickupAnim, rodAsStick, itemLighting;
    public static int swingHasteInfluence = 100;
    /** Set by the container hooks while a container draws its labels: Dark Mode lightens their dark gray. Render thread only. */
    public static boolean containerLabels;
    /** The name tag drawing now (render thread): its alpha (Distance Fade) and scale; 1 for tags that are not a player's. */
    public static float tagAlpha = 1, tagScale = 1;

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
        LightOverlay.refresh();
        LootBeamsModule.refresh();
        MobOverlayModule.refresh();
        MobSizeModule.refresh();
        ItemDespawnModule.refresh();
        UhcOverlayModule.refresh();

        ModuleManager modules = ModuleManager.getInstance();
        Module tweaks = on(modules.getModule(RENDER_TWEAKS));
        hideShadows = tweaks != null && !bool(tweaks, SHADOWS);
        hideLightning = tweaks != null && bool(tweaks, HIDE_LIGHTNING);
        hideFishingLine = tweaks != null && bool(tweaks, HIDE_FISHING_LINE);
        hideElderGuardian = tweaks != null && bool(tweaks, HIDE_ELDER_GUARDIAN);
        bobbingDisconnect = tweaks != null && bool(tweaks, BOBBING_DISCONNECT);

        Module overlays = on(modules.getModule(OVERLAYS));
        fireOpacity = percent(overlays, FIRE_OPACITY);
        pumpkinOpacity = percent(overlays, PUMPKIN_OPACITY);
        portalOpacity = percent(overlays, PORTAL_OPACITY);
        hideFireWithResistance = overlays != null && bool(overlays, FIRE_RESISTANCE);

        Module totem = on(modules.getModule(TOTEM));
        hideTotem = totem != null && bool(totem, HIDE_TOTEM);
        totemScale = percent(totem, TOTEM_SCALE);

        Module dark = on(modules.getModule(DARK_MODE));
        darkFill = dark == null ? 0 : darkFill(percent(dark, DARKNESS));

        Module tags = on(modules.getModule("Nametags"));
        nametagScale = percent(tags, "Scale");
        nametagBackground = tags == null ? -1 : Math.round(percent(tags, "Background Opacity") * 255);
        nametagFade = tags != null && bool(tags, "Distance Fade");
        nametagPing = tags != null && bool(tags, "Show Ping");
        nametagsInF1 = tags != null && bool(tags, "Show with Hidden HUD");
        nametagHealth = tags != null && on(modules.getModule(NAMETAG_HEALTH)) != null;

        OldAnimationsModule anims = modules.getModule(OldAnimationsModule.NAME) instanceof OldAnimationsModule found ? found : null;
        constantSwing = anims != null && anims.active(OldAnimationsModule.Feature.CONSTANT_SWING, OldAnimationsModule.Platform.MODERN);
        noReequip = anims != null && anims.active(OldAnimationsModule.Feature.NO_REEQUIP, OldAnimationsModule.Platform.MODERN);
        pickupAnim = anims != null && anims.pickupAnim(OldAnimationsModule.Platform.MODERN);
        rodAsStick = anims != null && anims.rodAsStick(OldAnimationsModule.Platform.MODERN);
        itemLighting = anims != null && anims.itemLighting(OldAnimationsModule.Platform.MODERN);
        swingHasteInfluence = anims != null ? anims.swingSpeedHaste.getIntValue() : 100;
    }

    /** A name tag starts drawing: players' tags get Scale and Distance Fade; holograms and named mobs keep vanilla's. */
    public static void tagStart(boolean player, double distanceSq) {
        tagAlpha = player ? fade(distanceSq) : 1;
        tagScale = player ? nametagScale : 1;
    }

    /** A tag's background alpha (0 to 1) from vanilla's: Background Opacity, then the tag's fade. */
    public static float background(float vanilla) {
        return (nametagBackground >= 0 ? nametagBackground / 255f : vanilla) * tagAlpha;
    }

    /** A colour setting's ARGB: its own, or with "Global" the HUD colour in its own alpha. */
    public static int argb(ColorOption option) {
        return argb(option.isUseGlobal(), option.getColor(), HudSettings.getInstance().getGlobalColor());
    }

    static int argb(boolean global, int own, int globalColor) {
        return global ? own & 0xFF000000 | globalColor & 0xFFFFFF : own;
    }

    /** {@code argb} with its alpha times {@code factor} (0 to 1). */
    public static int alpha(int argb, float factor) {
        int a = Math.round((argb >>> 24) * Math.max(0, Math.min(1, factor)));
        return a << 24 | argb & 0xFFFFFF;
    }

    /** Dark Mode's fill: black at {@code darkness} (0 to 1) opacity. */
    static int darkFill(float darkness) {
        return alpha(0xFF000000, darkness);
    }

    /** A container label's text color: the dark gray turns light while Dark Mode is on (its alpha bits, often 0, stay). */
    public static int label(int color) {
        return containerLabels && darkFill != 0 && (color & 0xFFFFFF) == LABEL ? color & 0xFF000000 | LIGHT_LABEL : color;
    }

    /** Distance Fade: a tag is fully drawn up to 16 blocks away, then fades to a quarter at 64 (vanilla's name tag range). */
    public static float fade(double distanceSq) {
        if (!nametagFade) return 1;
        double d = Math.sqrt(distanceSq);
        return d <= 16 ? 1 : (float) Math.max(0.25, 1 - (d - 16) / 48 * 0.75);
    }

    /** Show Ping: the color of a latency, as the tab list's bars (green to red). */
    public static int pingColor(int ms) {
        return ms < 0 ? 0xFFAAAAAA : ms < 150 ? 0xFF55FF55 : ms < 300 ? 0xFFFFFF55 : ms < 600 ? 0xFFFFAA00 : 0xFFFF5555;
    }

    private static final String[] NUMBERS = new String[1000];

    /** "0".."999" without a new String per tag per frame (the ping and health on name tags). */
    public static String number(int value) {
        int v = Math.max(0, Math.min(999, value));
        String text = NUMBERS[v];
        if (text == null) NUMBERS[v] = text = Integer.toString(v);
        return text;
    }

    private static Module on(Module module) {
        return module != null && module.isEnabled() ? module : null;
    }

    private static boolean bool(Module module, String name) {
        return module.getOption(name) instanceof BoolOption option && option.get();
    }

    /** A percent slider as a factor; 1 (vanilla) while its module is off or missing. */
    private static float percent(Module module, String name) {
        return module != null && module.getOption(name) instanceof SliderOption slider ? (float) slider.getValue() / 100f : 1;
    }
    public static int getSwingDuration(int haste, int fatigue, int base) {
        if (constantSwing) return base;
        return OldAnimations.swingDuration(haste, fatigue, base, swingHasteInfluence);
    }
}
