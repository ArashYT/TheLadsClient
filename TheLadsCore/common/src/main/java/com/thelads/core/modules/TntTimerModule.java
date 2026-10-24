package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * TNT Timer (1.9.1): the time left above each primed TNT (and lit TNT minecart), "3.45s", sliding green, yellow, red towards 0. The
 * fuse is the one the client counts down every tick, interpolated with the frame's partial tick. Like a name tag it shows within 64
 * blocks and is hidden behind blocks unless Through blocks is on. Hypixel allows TNT timers.
 */
public final class TntTimerModule extends Module {
    public static final String NAME = "TNT Timer";
    /** Vanilla's name tag range, in blocks. */
    public static final double RANGE = 64;
    /** Ticks at which the colour is fully green: vanilla's 80-tick (4 s) fuse. */
    static final float GREEN_TICKS = 80;

    public final SliderOption size = addOption(new SliderOption("Size", 100, 50, 200, 10).percent());
    public final BoolOption background = addOption(new BoolOption("Background", true));
    public final BoolOption shadow = addOption(new BoolOption("Text Shadow", true));
    public final BoolOption minecarts = addOption(new BoolOption("TNT minecarts", true));
    public final BoolOption throughBlocks = addOption(new BoolOption("Through blocks", false));

    private static final String[] FUSE_TABLE = new String[200];
    private static final String[] CENTI_TABLE = new String[1000];
    private static TntTimerModule cachedModule;

    static {
        for (int c = 0; c < CENTI_TABLE.length; c++) {
            int part = c % 100;
            CENTI_TABLE[c] = c / 100 + (part < 10 ? ".0" : ".") + part + "s";
        }
        for (int t = 0; t < FUSE_TABLE.length; t++) {
            FUSE_TABLE[t] = CENTI_TABLE[t * 5];
        }
    }

    public TntTimerModule() {
        super(NAME, "Shows the seconds left above primed TNT and lit TNT minecarts, turning from green to red as it nears 0.");
        setEnabled(true);
    }

    /** The module when it is on, else null. */
    public static TntTimerModule active() {
        TntTimerModule module = cachedModule;
        if (module == null) {
            module = ModuleManager.getInstance().getModule(NAME) instanceof TntTimerModule m ? m : null;
            cachedModule = module;
        }
        return module != null && module.isEnabled() ? module : null;
    }

    /** Text scale: vanilla's name tag size (0.025 blocks per font pixel) times Size. */
    public float scale() { return 0.025f * size.getIntValue() / 100f; }

    /** Drawn at this squared distance from the camera: within name tag range, or anywhere with Through blocks. */
    public boolean shows(double distanceSq) { return seeThrough() || distanceSq <= RANGE * RANGE; }

    /** Through blocks, except on Hypixel: there a timer seen through walls would count as ESP, so it is depth tested like a name tag. */
    public boolean seeThrough() { return throughBlocks.get() && !com.thelads.core.client.HypixelSafetyManager.isDisallowed(com.thelads.core.client.HypixelSafetyManager.TNT_THROUGH_BLOCKS); }

    /** Precomputed O(1) lookup for integer fuse ticks [0..199]. */
    public static String format(int fuseTicks) {
        if (fuseTicks >= 0 && fuseTicks < FUSE_TABLE.length) return FUSE_TABLE[fuseTicks];
        return label(fuseTicks);
    }

    /** "3.45s": the ticks left as seconds with two decimals, never below 0. */
    public static String label(float ticksLeft) {
        int centis = Math.round(Math.max(0f, ticksLeft) * 5f); // ticks / 20 s * 100
        if (centis >= 0 && centis < CENTI_TABLE.length) return CENTI_TABLE[centis];
        int part = centis % 100;
        return centis / 100 + (part < 10 ? ".0" : ".") + part + "s";
    }

    /** Opaque ARGB sliding green (4 s and more), yellow (2 s), red (0 s). */
    public static int color(float ticksLeft) {
        float f = Math.max(0f, Math.min(1f, ticksLeft / GREEN_TICKS));
        int red = f >= 0.5f ? Math.round((1f - f) * 2f * 255f) : 255;
        int green = f >= 0.5f ? 255 : Math.round(f * 2f * 255f);
        return 0xFF000000 | red << 16 | green << 8;
    }
}
