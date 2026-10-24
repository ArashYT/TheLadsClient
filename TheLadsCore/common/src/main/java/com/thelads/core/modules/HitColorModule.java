package com.thelads.core.modules;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;

/**
 * Hit Color (Lunar, Badlion, OneClient's DamageTint): the colour and strength of the red flash a hurt or dying mob or player shows,
 * its armour too with 1.7 Animations' red armour. The colour's alpha is the strength: Minecraft mixes 30% red into the model.
 * 1.20.1 and 26.x keep the flash in the entity overlay texture, which the adapter repaints when the colour changes;
 * 1.8.9 sets it per entity in RendererLivingEntity.setBrightness.
 * Also supports per-damage-type hit flashes (Fire/Lava, Fall, Drowning, Default).
 */
public final class HitColorModule extends Module {
    public static final String NAME = "Hit Color";
    /** Minecraft's flash: red at 30%. */
    public static final int VANILLA = 0x4DFF0000;
    public static final int DEFAULT_FIRE = 0x4DFF6600;
    public static final int DEFAULT_FALL = 0x4DA0522D;
    public static final int DEFAULT_DROWN = 0x4D0080FF;

    public enum DamageType {
        DEFAULT(3),
        FIRE(1),
        FALL(2),
        DROWNING(4);

        public final int overlayV;
        DamageType(int overlayV) { this.overlayV = overlayV; }
    }

    public final BoolOption perDamageType = addOption(new BoolOption("Per damage type", true));
    public final ColorOption color = addOption(new ColorOption("Color", false, VANILLA));
    public final ColorOption fireColor = addOption(new ColorOption("Fire / Lava", false, DEFAULT_FIRE));
    public final ColorOption fallColor = addOption(new ColorOption("Fall", false, DEFAULT_FALL));
    public final ColorOption drownColor = addOption(new ColorOption("Drowning", false, DEFAULT_DROWN));

    /** Cached each tick: on, and the flash's ARGB. Off, each adapter leaves its version's own flash. */
    public boolean on;
    public int argb = VANILLA;
    public int fireArgb = DEFAULT_FIRE;
    public int fallArgb = DEFAULT_FALL;
    public int drownArgb = DEFAULT_DROWN;

    private static final int RECENT_CAP = 128;
    private static final int[] RECENT_IDS = new int[RECENT_CAP];
    private static final DamageType[] RECENT_TYPES = new DamageType[RECENT_CAP];
    private static final long[] RECENT_EXPIRES = new long[RECENT_CAP];
    private static int recentIndex = 0;

    public HitColorModule() {
        super(NAME, "The colour and strength (alpha) of the flash hurt mobs and players show, with per-damage-type tints.");
    }

    public void refresh() {
        on = isEnabled();
        argb = PvpVisuals.argb(color);
        fireArgb = PvpVisuals.argb(fireColor);
        fallArgb = PvpVisuals.argb(fallColor);
        drownArgb = PvpVisuals.argb(drownColor);
    }

    public int getColor(DamageType type) {
        if (!perDamageType.get()) return argb;
        if (type == null) return argb;
        switch (type) {
            case FIRE: return fireArgb;
            case FALL: return fallArgb;
            case DROWNING: return drownArgb;
            default: return argb;
        }
    }

    public static synchronized void recordDamage(int entityId, DamageType type) {
        long expires = System.currentTimeMillis() + 1000L;
        for (int i = 0; i < RECENT_CAP; i++) {
            if (RECENT_IDS[i] == entityId) {
                RECENT_TYPES[i] = type;
                RECENT_EXPIRES[i] = expires;
                return;
            }
        }
        int slot = (recentIndex++) & (RECENT_CAP - 1);
        RECENT_IDS[slot] = entityId;
        RECENT_TYPES[slot] = type;
        RECENT_EXPIRES[slot] = expires;
    }

    public static synchronized DamageType getDamageType(int entityId) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < RECENT_CAP; i++) {
            if (RECENT_IDS[i] == entityId) {
                if (now <= RECENT_EXPIRES[i]) {
                    return RECENT_TYPES[i];
                }
                break;
            }
        }
        return DamageType.DEFAULT;
    }

    public static DamageType classifyDamageSource(String name, boolean isFire, boolean isFall, boolean isDrown) {
        if (isFire) return DamageType.FIRE;
        if (isFall) return DamageType.FALL;
        if (isDrown) return DamageType.DROWNING;
        if (name != null) {
            String lower = name.toLowerCase();
            if (lower.contains("fire") || lower.contains("lava") || lower.contains("cactus") || lower.contains("hotfloor") || lower.contains("campfire")) {
                return DamageType.FIRE;
            }
            if (lower.contains("fall") || lower.contains("flyintowall") || lower.contains("stalagmite")) {
                return DamageType.FALL;
            }
            if (lower.contains("drown") || lower.contains("dryout")) {
                return DamageType.DROWNING;
            }
        }
        return DamageType.DEFAULT;
    }

    /**
     * The overlay texture's hurt pixel (ARGB) for a flash: the shader keeps the model's colour by the pixel's alpha, so the flash's
     * strength becomes its complement.
     */
    public static int overlayPixel(int argb) {
        return (255 - (argb >>> 24)) << 24 | argb & 0xFFFFFF;
    }

    /** ARGB in NativeImage's ABGR order (1.20.1 setPixelRGBA). */
    public static int abgr(int argb) {
        return argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF;
    }
}
