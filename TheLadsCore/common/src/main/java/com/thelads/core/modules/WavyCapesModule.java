package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Wavy Capes (1.9.1): the cape hangs as a chain of strips that swings with movement, falls, jumps, sneaking and wind instead of the
 * one rigid plane (client.WavyCape holds the physics and the mesh; each version draws that mesh in place of its cape model). Elytra
 * and armour keep their vanilla rules: no cape under an elytra, the armour offset as vanilla. On by default: it only changes how capes
 * this client already shows are drawn.
 */
public final class WavyCapesModule extends Module {
    public static final String NAME = "Wavy Capes";
    /** Strips are cheap but not free: 32 is two strips per texture pixel row, more than any texture shows. */
    public static final int MAX_SEGMENTS = 32;

    public final DropdownOption style = addOption(new DropdownOption("Style", 0, "Smooth", "Blocky"));
    public final SliderOption wind = addOption(new SliderOption("Wind", 30, 0, 100, 5).percent());
    public final SliderOption gravity = addOption(new SliderOption("Gravity", 50, 10, 100, 5).percent());
    public final SliderOption segments = addOption(new SliderOption("Segments", 16, 4, MAX_SEGMENTS, 2));
    public final BoolOption others = addOption(new BoolOption("Other players", true));

    private static WavyCapesModule cachedModule;

    public WavyCapesModule() {
        super(NAME, "Capes flow with your movement, jumps and the wind instead of hanging as one stiff plate. Style, wind, weight and "
            + "smoothness are adjustable; elytra and armour are untouched.");
        setEnabled(true);
    }

    public boolean blocky() { return style.getIndex() == 1; }

    /** Strips the cape is cut into (Segments, 4 to MAX_SEGMENTS). */
    public int segmentCount() { return Math.max(4, Math.min(MAX_SEGMENTS, segments.getIntValue())); }

    /** The module when it should draw this cape: on, and for another player's cape only with Other players on. Else null. */
    public static WavyCapesModule active(boolean ownCape) {
        WavyCapesModule module = cachedModule;
        if (module == null) {
            module = ModuleManager.getInstance().getModule(NAME) instanceof WavyCapesModule m ? m : null;
            cachedModule = module;
        }
        return module != null && module.isEnabled() && (ownCape || module.others.get()) ? module : null;
    }
}
