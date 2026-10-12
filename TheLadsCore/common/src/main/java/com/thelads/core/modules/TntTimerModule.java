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

    public TntTimerModule() {
        super(NAME, "Shows the seconds left above primed TNT and lit TNT minecarts, turning from green to red as it nears 0.");
        setEnabled(true);
    }

    /** The module when it is on, else null. */
    public static TntTimerModule active() {
        TntTimerModule module = ModuleManager.getInstance().getModule(NAME) instanceof TntTimerModule m ? m : null;
        return module != null && module.isEnabled() ? module : null;
    }

    /** Text scale: vanilla's name tag size (0.025 blocks per font pixel) times Size. */
    public float scale() { return 0.025f * size.getIntValue() / 100f; }

    /** Drawn at this squared distance from the camera: within name tag range, or anywhere with Through blocks. */
    public boolean shows(double distanceSq) { return throughBlocks.get() || distanceSq <= RANGE * RANGE; }

    /** "3.45s": the ticks left as seconds with two decimals, never below 0. */
    public static String label(float ticksLeft) {
        int centis = Math.round(Math.max(0f, ticksLeft) * 5f); // ticks / 20 s * 100
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
