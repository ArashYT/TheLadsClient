package com.thelads.core.client;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ElytraSwapTest {
    @Test
    void findSwapSlotLookingForChestplate() {
        List<String> inv = List.of(
            "minecraft:diamond_sword",
            "minecraft:golden_apple",
            "minecraft:netherite_chestplate",
            "minecraft:dirt"
        );
        // Currently wearing elytra -> should find netherite_chestplate at index 2
        assertEquals(2, ElytraSwap.findSwapSlot(true, inv));
    }

    @Test
    void findSwapSlotLookingForElytra() {
        List<String> inv = List.of(
            "minecraft:cooked_beef",
            "minecraft:elytra",
            "minecraft:diamond_chestplate"
        );
        // Currently wearing chestplate -> should find elytra at index 1
        assertEquals(1, ElytraSwap.findSwapSlot(false, inv));
    }

    @Test
    void findSwapSlotNotFound() {
        List<String> inv = List.of("minecraft:stone", "minecraft:dirt");
        assertEquals(-1, ElytraSwap.findSwapSlot(true, inv));
        assertEquals(-1, ElytraSwap.findSwapSlot(false, inv));
        assertEquals(-1, ElytraSwap.findSwapSlot(true, null));
    }
}
