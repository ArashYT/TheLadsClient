package com.thelads.core.modules;

import com.thelads.core.config.ActionOption;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Freelook (Lunar/Feather/Badlion "Perspective"): while its key is held (or toggled), the camera orbits the player in third person and
 * the mouse turns only the camera; the player keeps aiming where it did. Snaplook: while its key is held, the front view (look behind).
 * Both keys are unbound. The adapters send the keys and gameplay stops ({@link #release}), apply {@link #perspective} every client
 * tick and, while {@link #detached}, draw the camera at {@link #yaw}/{@link #pitch} and hand it the mouse ({@link #turn}).
 * Server rules: a camera that leaves the aim is banned on some servers, so HypixelSafetyManager keeps the module off there and where
 * the rules are unknown (per-server bypass through the red warning only).
 */
public final class FreelookModule extends Module {
    public static final String NAME = "Freelook";
    /** Perspectives as 1.8.9's thirdPersonView and the ordinals of 26.x's CameraType: first person, behind, in front. */
    public static final int FIRST_PERSON = 0, BACK = 1, FRONT = 2;
    private static final int HOLD = 0, NONE = -1;
    private static volatile FreelookModule cached;

    public final DropdownOption mode;
    public final BoolOption invertPitch;
    public final ActionOption freelookKey, snaplookKey;
    private boolean free, snap, detached;
    /** The perspective this module set (NONE: the player's own) and the player's own from before. */
    private int applied = NONE, saved;
    private float yaw, pitch;

    public FreelookModule() {
        super(NAME, "Hold the Freelook key to look around your player in third person without turning; hold the Snaplook key to look "
            + "behind you. Set both keys under Controls. Off on servers that ban it or whose rules are unknown (Bypass at your own risk).");
        mode = addOption(new DropdownOption("Mode", HOLD, "Hold", "Toggle"));
        invertPitch = addOption(new BoolOption("Invert Pitch", false));
        freelookKey = addOption(new ActionOption("Freelook key", "Controls"));
        snaplookKey = addOption(new ActionOption("Snaplook key", "Controls"));
    }

    public static FreelookModule get() {
        FreelookModule module = cached;
        if (module == null && ModuleManager.getInstance().getModule(NAME) instanceof FreelookModule m) cached = module = m;
        return module;
    }

    /** The Freelook key went down or up during gameplay. */
    public void key(boolean down) {
        if (mode.getIndex() == HOLD) free = down;
        else if (down) free = !free;
    }

    /** The Snaplook key went down or up during gameplay. */
    public void snapKey(boolean down) {
        snap = down;
    }

    /** Gameplay stopped (a screen, focus loss, death, another world): both views end at the next tick. */
    public void release() {
        free = snap = false;
    }

    /**
     * Every client tick: the perspective the game should use, given the one it uses now and where the player looks. Starting Freelook
     * or Snaplook remembers the player's own perspective, ending them brings it back; Freelook's camera starts at the player's aim.
     * Off (or blocked by the server's rules) both end at once.
     */
    public int perspective(int current, float playerYaw, float playerPitch) {
        int want = !isEnabled() ? NONE : snap ? FRONT : free ? BACK : NONE;
        if (want == applied) return current;
        if (applied == NONE) saved = current;
        if (want == BACK) {
            yaw = playerYaw;
            pitch = playerPitch;
        }
        applied = want;
        detached = want == BACK;
        return want == NONE ? saved : want;
    }

    /** The camera is Freelook's: it is drawn at {@link #yaw}/{@link #pitch}, and the mouse turns it instead of the player. */
    public boolean detached() {
        return detached;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    /** The mouse moved the view by these degrees (pitch positive = down): true when Freelook's camera took them, not the player. */
    public boolean turn(double yawDegrees, double pitchDegrees) {
        if (!detached) return false;
        yaw += (float) yawDegrees;
        pitch = Math.max(-90, Math.min(90, pitch + (float) (invertPitch.get() ? -pitchDegrees : pitchDegrees)));
        return true;
    }
}
