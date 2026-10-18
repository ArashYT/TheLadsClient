package com.thelads.smp.features.arise.nb5.client;

import net.minecraft.client.KeyMapping;

import java.util.Arrays;

/**
 * Keybindings for NB5 parkour actions: Dodge/Roll, Ledge Grab, and Slide.
 */
public final class ParkourKeyMappings {
    public static final KeyMapping KEY_DODGE_ROLL = new KeyMapping(
            "key.ladssmp.dodge_roll",
            342, // Left Alt
            KeyMapping.Category.MOVEMENT
    );

    public static final KeyMapping KEY_LEDGE_GRAB = new KeyMapping(
            "key.ladssmp.ledge_grab",
            71, // 'G'
            KeyMapping.Category.MOVEMENT
    );

    public static final KeyMapping KEY_SLIDE = new KeyMapping(
            "key.ladssmp.slide",
            67, // 'C'
            KeyMapping.Category.MOVEMENT
    );

    private ParkourKeyMappings() {}

    public static void init() {
        // Statically instantiated, registers in KeyMapping.ALL
    }

    public static KeyMapping[] appendKeys(KeyMapping[] original) {
        if (original == null) return new KeyMapping[]{KEY_DODGE_ROLL, KEY_LEDGE_GRAB, KEY_SLIDE};
        for (KeyMapping k : original) {
            if (k == KEY_DODGE_ROLL) return original; // Already appended
        }
        KeyMapping[] copy = Arrays.copyOf(original, original.length + 3);
        copy[original.length] = KEY_DODGE_ROLL;
        copy[original.length + 1] = KEY_LEDGE_GRAB;
        copy[original.length + 2] = KEY_SLIDE;
        return copy;
    }
}
