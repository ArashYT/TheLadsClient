package com.thelads.smp.features;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DeathBackupManagerTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.DIAMOND.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.AIR.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    @Test
    void testItemStackCodecRoundtrip() {
        RegistryAccess registryAccess = RegistryAccess.EMPTY;
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);

        // 1. Test fallback deserialize with legacy snapshot format
        JsonObject legacy = new JsonObject();
        legacy.addProperty("slot", 4);
        legacy.addProperty("id", "minecraft:iron_ingot");
        legacy.addProperty("count", 10);
        ItemStack legacyRestored = DeathBackupManager.deserializeStack(ops, legacy);
        assertNotNull(legacyRestored);
        assertEquals(Items.IRON_INGOT, legacyRestored.getItem());
        assertEquals(10, legacyRestored.getCount());

        // 2. Test serializeStack with empty stack
        JsonObject emptyJson = DeathBackupManager.serializeStack(ops, ItemStack.EMPTY, 1);
        assertEquals(1, emptyJson.get("slot").getAsInt());
        assertEquals("minecraft:air", emptyJson.get("id").getAsString());
        assertEquals(ItemStack.EMPTY, DeathBackupManager.deserializeStack(ops, emptyJson));

        // 3. Test CODEC serialized stack roundtrip
        JsonObject stackJson = new JsonObject();
        stackJson.addProperty("id", "minecraft:diamond");
        stackJson.addProperty("count", 7);
        JsonObject entry = new JsonObject();
        entry.addProperty("slot", 0);
        entry.addProperty("id", "minecraft:diamond");
        entry.addProperty("count", 7);
        entry.add("stack", stackJson);

        ItemStack restored = DeathBackupManager.deserializeStack(ops, entry);
        assertNotNull(restored);
        assertFalse(restored.isEmpty());
        assertEquals(Items.DIAMOND, restored.getItem());
        assertEquals(7, restored.getCount());

        // 4. Test re-serializing the restored stack and verifying "stack" property is preserved
        JsonObject reSerialized = DeathBackupManager.serializeStack(ops, restored, 0);
        assertEquals(0, reSerialized.get("slot").getAsInt());
        assertEquals("minecraft:diamond", reSerialized.get("id").getAsString());
        assertEquals(7, reSerialized.get("count").getAsInt());
        assertTrue(reSerialized.has("stack"));

        ItemStack finalRestored = DeathBackupManager.deserializeStack(ops, reSerialized);
        assertEquals(Items.DIAMOND, finalRestored.getItem());
        assertEquals(7, finalRestored.getCount());
    }
}
