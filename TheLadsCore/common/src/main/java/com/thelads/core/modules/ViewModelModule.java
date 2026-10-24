package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * ViewModel (Lunar Item Position, Badlion Item Size, OneClient Custom Viewmodel/Hand FOV): moves, turns and scales the first-person
 * hand and held item, and sets the field of view they are drawn with. Each version applies {@link #x} ... {@link #scale} right after
 * the hand's own pose push: offset first (mirrored for the left hand), then rotation and scale around where vanilla holds the item
 * ({@link #PIVOT_X}, {@link #PIVOT_Y}, {@link #PIVOT_Z}, the first-person item position every version shares).
 */
public final class ViewModelModule extends Module {
    public static final String NAME = "ViewModel";
    public static final float PIVOT_X = 0.56f, PIVOT_Y = -0.52f, PIVOT_Z = -0.72f;
    /** The field of view every version draws the hand with (before death/water changes). */
    public static final double VANILLA_HAND_FOV = 70;
    private static volatile ViewModelModule cached;

    public final SliderOption x, y, z, rotX, rotY, rotZ, scale, handFov;

    public ViewModelModule() {
        super(NAME, "Move, rotate and resize your hand and held item, and change the field of view they are drawn with.");
        x = addOption(new SliderOption("Position X", 0, -1, 1, 0.05));
        y = addOption(new SliderOption("Position Y", 0, -1, 1, 0.05));
        z = addOption(new SliderOption("Position Z", 0, -1, 1, 0.05));
        rotX = addOption(new SliderOption("Rotation X", 0, -180, 180, 5));
        rotY = addOption(new SliderOption("Rotation Y", 0, -180, 180, 5));
        rotZ = addOption(new SliderOption("Rotation Z", 0, -180, 180, 5));
        scale = addOption(new SliderOption("Scale", 100, 10, 200, 5).percent());
        handFov = addOption(new SliderOption("Hand FOV", VANILLA_HAND_FOV, 30, 110, 1).editable());
    }

    public static ViewModelModule get() {
        ViewModelModule module = cached;
        if (module == null && ModuleManager.getInstance().getModule(NAME) instanceof ViewModelModule m) cached = module = m;
        return module;
    }

    /** The hand's field of view: the game's (zoom, water and death included) scaled to Hand FOV; the game's own while off. */
    public float handFov(float fov) {
        return isEnabled() ? (float) (fov * handFov.getValue() / VANILLA_HAND_FOV) : fov;
    }

    /** The hand pose moves at all: false while off or with every slider at its default, so the adapters skip the matrix work. */
    public boolean posed() {
        return isEnabled() && (x.getValue() != 0 || y.getValue() != 0 || z.getValue() != 0 || rotX.getValue() != 0 || rotY.getValue() != 0
            || rotZ.getValue() != 0 || scale.getValue() != 100);
    }
}
