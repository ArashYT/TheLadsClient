package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Mob Overlay (Feather) and Highlight Shulker Bullets (Badlion): hostile and passive mobs, and shulker bullets (1.9 and later), get a
 * colored outline drawn through walls (the glowing outline; on 1.8.9 the spectator outline pass). Outlines through walls show what
 * vanilla does not: a risky module (HypixelSafetyManager), off by default. Players keep their vanilla outlines.
 */
public final class MobOverlayModule extends Module {
    public static final String NAME = "Mob Overlay";
    /** What a version reports an entity as. */
    public static final int OTHER = 0, HOSTILE = 1, PASSIVE = 2, BULLET = 3;
    private static boolean active;
    private static final boolean[] ON = new boolean[4];
    private static final int[] COLOR = new int[4];

    public final BoolOption hostile = addOption(new BoolOption("Hostile mobs", true));
    public final ColorOption hostileColor = addOption(new ColorOption("Hostile color", false, 0xFFFF5555));
    public final BoolOption passive = addOption(new BoolOption("Passive mobs", false));
    public final ColorOption passiveColor = addOption(new ColorOption("Passive color", false, 0xFF55FF55));
    public final BoolOption bullets = addOption(new BoolOption("Shulker bullets", true));
    public final ColorOption bulletColor = addOption(new ColorOption("Bullet color", false, 0xFFFFFF55));

    public MobOverlayModule() {
        super(NAME, "Colored outlines on hostile and passive mobs and on shulker bullets (1.9+), seen through walls.");
    }

    /** Reads the settings into fields (each version's client tick), so the per-entity checks read fields only. */
    public static void refresh() {
        MobOverlayModule module = ModuleManager.getInstance().getModule(NAME) instanceof MobOverlayModule m ? m : null;
        active = module != null && module.isEnabled();
        if (!active) return;
        ON[HOSTILE] = module.hostile.get(); COLOR[HOSTILE] = module.hostileColor.getColor() & 0xFFFFFF;
        ON[PASSIVE] = module.passive.get(); COLOR[PASSIVE] = module.passiveColor.getColor() & 0xFFFFFF;
        ON[BULLET] = module.bullets.get(); COLOR[BULLET] = module.bulletColor.getColor() & 0xFFFFFF;
    }

    public static boolean active() { return active; }

    /** Whether an entity of this kind ({@link #HOSTILE}, {@link #PASSIVE}, {@link #BULLET}) gets an outline right now. */
    public static boolean outlined(int kind) { return active && kind != OTHER && ON[kind]; }

    /** The outline's RGB for this kind. */
    public static int color(int kind) { return COLOR[kind]; }
}
