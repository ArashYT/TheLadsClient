package com.thelads.core.client.ladsapi.minigame;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * What the common layer cannot do itself, filled in by each game version at startup (before it declares the matching cap in
 * {@link Caps}). A hook that is null makes its feature do nothing: no hook, no effect, never an error.
 */
public final class Hooks {
    private Hooks() {}

    public enum Device { UNKNOWN, KBM, GAMEPAD }

    public enum Pad { XBOX, PLAYSTATION, SWITCH, GENERIC }

    /** Which device the player used last (cap {@code input}). Read, never polled by a timer. */
    public interface InputSource {
        Device device();

        /** The class of the pad while {@link #device()} is GAMEPAD; never a device name or id. */
        Pad pad();
    }

    /** Gamepad motors (cap {@code rumble}). Called only while a gamepad is the active device and {@link #vibrationScale()} is above 0. */
    public interface RumbleSink {
        /** The player's own vibration setting, 0 (off) to 1. */
        double vibrationScale();

        /** Both motors 0-1 already scaled by the setting; the sink ends it by itself after {@code ms}. */
        void rumble(double low, double high, int ms);

        void stop();
    }

    /** Plays sound events the game already has (cap {@code audio}). Volumes are 0-1 before the player's own slider. */
    public interface SoundPlayer {
        /** False when the sound event is unknown here: the cue is then ignored. */
        boolean play(AudioState.Voice voice, double volume);

        void volume(String id, double volume);

        void stop(String id);

        /** Whether a sound started by {@link #play} is still audible (one-shots end on their own). */
        boolean playing(String id);

        /** Vanilla background music held quiet while any cue asked for it ({@code music_pause}). */
        void musicHold(boolean hold);
    }

    /** The player's own saved Lads profiles (cap {@code profile}). */
    public interface ProfileSwitcher {
        /** Name of the active profile, or null. */
        String current();

        /** Switches to the profile of that name (case-insensitive). False when the player has none: nothing changes. */
        boolean switchTo(String name);
    }

    /** Entities the client already has loaded (waypoints, camera targets). Never ask the server and never reveal an unloaded one. */
    public interface EntityLookup {
        /** The feet position {x,y,z} of a loaded, rendered entity, or null. */
        double[] position(UUID id);
    }

    /** The "Server" group in Controls (cap {@code action}). A press goes out through {@code LadsApiClient.serverAction}. */
    public interface ServerKeys {
        /** Whether the key (an options.txt name) is not used by any vanilla or mod binding: else the action starts unbound. */
        default boolean keyFree(String optionsKeyName) {
            return true;
        }

        /** The full current list (empty = remove them all); called after every {@code actions} message and on reset. */
        void changed(List<ActionsState.Action> actions);
    }

    public static volatile InputSource input;
    public static volatile RumbleSink rumble;
    public static volatile SoundPlayer sound;
    public static volatile ProfileSwitcher profiles;
    public static volatile EntityLookup entities;
    public static volatile ServerKeys keys;
    /** The player's reduce-flashes accessibility setting: {@code flash} and {@code pulse_ms} are ignored while it is on. */
    public static volatile BooleanSupplier reduceFlashes = () -> false;
    /** "Let servers change my Lads settings" is off: a {@code profile} message does nothing. */
    public static volatile BooleanSupplier refusesServerChanges = com.thelads.core.client.ladsapi.LadsApiClient::playerRefusesChanges;
    /** Whether a marker may be drawn through blocks here (the radar-like rule of HypixelSafetyManager); false when unsure. */
    public static volatile BooleanSupplier throughWallsAllowed = () -> {
        try {
            return com.thelads.core.client.HypixelSafetyManager.isAllowed("Minimap");
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    };

    /** For tests: every hook back to its default. */
    public static void reset() {
        input = null;
        rumble = null;
        sound = null;
        profiles = null;
        entities = null;
        keys = null;
        reduceFlashes = () -> false;
        refusesServerChanges = com.thelads.core.client.ladsapi.LadsApiClient::playerRefusesChanges;
    }
}
