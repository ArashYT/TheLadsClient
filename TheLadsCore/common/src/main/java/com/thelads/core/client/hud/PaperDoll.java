package com.thelads.core.client.hud;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.PlayerActionOption;
import com.thelads.core.config.SliderOption;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The paper doll between frames (the Paperdoll module, drawn in PaperdollHudElement's box): how long it stays up after the last
 * trigger, and how far its head leans into a turn. Each version calls {@link #tick} once per client tick in a world and draws its
 * player with the angles below.
 */
public final class PaperDoll {
    public static final PaperDoll INSTANCE = new PaperDoll(() -> ModuleManager.getInstance().getModule("Paperdoll"));
    /** Head lean per degree turned in a tick, and the share of the gap to that lean closed each tick. */
    private static final float LEAN_PER_DEGREE = 2.5f, EASE = 0.3f;
    private final Supplier<Module> module;
    private int ticksLeft;
    private float lean, previousLean;

    PaperDoll(Supplier<Module> module) {
        this.module = module;
    }

    /**
     * One client tick. happening: whether a trigger (a {@link PlayerActionOption} name) is happening now; turned: degrees the
     * player's view turned right since the last tick (negative: left).
     */
    public void tick(Predicate<String> happening, float turned) {
        Module doll = module.get();
        if (doll == null) return;
        boolean triggered = false;
        for (Option option : doll.getOptions())
            if (option instanceof PlayerActionOption action) {
                action.detect(happening.test(action.getName()));
                triggered |= action.get() && action.detected();
            }
        if (!doll.isEnabled()) {
            ticksLeft = 0;
            lean = previousLean = 0;
            return;
        }
        // Display Time counts from the moment the trigger stops; 0 shows the doll only while it lasts.
        ticksLeft = triggered ? 1 + Math.max(0, (int) number(doll, "Display Time (ticks)", 40)) : Math.max(0, ticksLeft - 1);
        float limit = number(doll, "Maximum Yaw", 30);
        float wanted = Float.isFinite(turned) ? Math.max(-limit, Math.min(limit, turned * LEAN_PER_DEGREE)) : 0;
        previousLean = lean;
        lean += (wanted - lean) * EASE;
    }

    /** Whether gameplay shows the doll now (the HUD editor always previews it). firstPerson: the camera. */
    public boolean visible(boolean firstPerson) {
        Module doll = module.get();
        if (doll == null || !doll.isEnabled() || !bool(doll, firstPerson ? "Show in First Person" : "Show in Third Person", true)) return false;
        return bool(doll, "Always Display", false) || ticksLeft > 0;
    }

    /** Body yaw to draw with (180 faces the viewer), turned toward the middle of the screen by Default Rotation. */
    public float bodyYaw(boolean leftHalfOfScreen) {
        float turn = number(module.get(), "Default Rotation", 15);
        return 180 + (leftHalfOfScreen ? -turn : turn);
    }

    /** Head yaw relative to the body: the lean into the current turn, when Head Movement follows yaw. */
    public float headYaw(float partialTick) {
        int mode = headMovement();
        if (mode != 0 && mode != 1) return 0;
        float partial = Float.isFinite(partialTick) ? Math.max(0, Math.min(1, partialTick)) : 1;
        return previousLean + (lean - previousLean) * partial;
    }

    /** Head pitch: the player's, within Maximum Pitch, when Head Movement follows pitch. */
    public float headPitch(float playerPitch) {
        int mode = headMovement();
        if ((mode != 0 && mode != 3) || !Float.isFinite(playerPitch)) return 0;
        float limit = number(module.get(), "Maximum Pitch", 30);
        return Math.max(-limit, Math.min(limit, playerPitch));
    }

    /** Model Opacity, 0.05 to 1. */
    public float opacity() {
        return number(module.get(), "Model Opacity", 100) / 100f;
    }

    /** Head Movement: 0 yaw and pitch, 1 yaw only, 2 none, 3 pitch only. */
    private int headMovement() {
        Module doll = module.get();
        return doll != null && doll.getOption("Head Movement") instanceof DropdownOption option ? option.getIndex() : 1;
    }

    private static float number(Module module, String name, float fallback) {
        return module != null && module.getOption(name) instanceof SliderOption option ? (float) option.getValue() : fallback;
    }

    private static boolean bool(Module module, String name, boolean fallback) {
        return module.getOption(name) instanceof BoolOption option ? option.get() : fallback;
    }
}
