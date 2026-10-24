package com.thelads.core.client.cosmetics;

import com.thelads.core.client.cosmetics.wearables.*;
import com.thelads.core.modules.CosmeticsModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CosmeticsWearablesTest {

    @BeforeEach
    void setUp() {
        CosmeticsManager.get().clear();
        CosmeticsModule module = CosmeticsModule.get();
        module.equippedCape.set("");
        module.equippedHat.set("");
        module.equippedGlasses.set("");
        module.visibility.setIndex(0); // Everyone
        module.showCapes.set(true);
        module.showHats.set(true);
        module.showGlasses.set(true);
        module.showOthers.set(true);
    }

    @Test
    void testCosmeticLibrary() {
        List<CosmeticItem> items = CosmeticLibrary.allItems();
        assertEquals(11, items.size(), "Should have 11 total cosmetics");

        assertEquals(4, CosmeticLibrary.getItems(CosmeticSlot.CAPE).size());
        assertEquals(4, CosmeticLibrary.getItems(CosmeticSlot.HAT).size());
        assertEquals(3, CosmeticLibrary.getItems(CosmeticSlot.GLASSES).size());

        CosmeticItem topHat = CosmeticLibrary.get("lads:hat_top");
        assertNotNull(topHat);
        assertEquals("Classic Top Hat", topHat.name());
        assertEquals(CosmeticSlot.HAT, topHat.slot());
    }

    @Test
    void testCosmeticModelsAndMeshes() {
        String[] modelIds = {
            "lads:hat_top", "lads:hat_halo", "lads:hat_crown", "lads:hat_beanie",
            "lads:glasses_sunglasses", "lads:glasses_goggles", "lads:glasses_bandana"
        };

        for (String id : modelIds) {
            List<CosmeticModelBox> boxes = CosmeticModels.getBoxes(id);
            assertFalse(boxes.isEmpty(), "Boxes should not be empty for " + id);

            float[] mesh = CosmeticModels.getMesh(id);
            assertNotNull(mesh, "Mesh should not be null for " + id);
            assertEquals(boxes.size() * 192, mesh.length, "Mesh length mismatch for " + id);

            // Verify vertex stride and UV boundaries [0, 1]
            for (int i = 0; i < mesh.length; i += CosmeticModels.STRIDE) {
                float u = mesh[i + 3];
                float v = mesh[i + 4];
                assertTrue(u >= 0f && u <= 1.0001f, "U out of bounds: " + u + " in " + id);
                assertTrue(v >= 0f && v <= 1.0001f, "V out of bounds: " + v + " in " + id);
            }
        }
    }

    @Test
    void testCosmeticsManagerEquipAndVisibility() {
        CosmeticsManager manager = CosmeticsManager.get();
        UUID localUuid = UUID.randomUUID();
        UUID remoteUuid = UUID.randomUUID();

        // Equip local
        manager.setLocalEquipped(CosmeticSlot.HAT, "lads:hat_crown");
        assertEquals("lads:hat_crown", manager.getLocalEquipped(CosmeticSlot.HAT));

        // Equip remote
        manager.onRemoteEquip(remoteUuid, "glasses", "lads:glasses_sunglasses");
        assertEquals("lads:glasses_sunglasses", manager.getEquippedGlasses(remoteUuid));

        // Visibility filter: Everyone (index 0)
        CosmeticsModule.get().visibility.setIndex(0);
        assertTrue(manager.shouldRender(localUuid, CosmeticSlot.HAT));
        assertTrue(manager.shouldRender(remoteUuid, CosmeticSlot.GLASSES));

        // Visibility filter: Self Only (index 2)
        CosmeticsModule.get().visibility.setIndex(2);
        assertTrue(manager.shouldRender(localUuid, CosmeticSlot.HAT));
        assertFalse(manager.shouldRender(remoteUuid, CosmeticSlot.GLASSES));

        // Visibility filter: None (index 3)
        CosmeticsModule.get().visibility.setIndex(3);
        assertFalse(manager.shouldRender(localUuid, CosmeticSlot.HAT));
        assertFalse(manager.shouldRender(remoteUuid, CosmeticSlot.GLASSES));

        // Slot visibility toggles
        CosmeticsModule.get().visibility.setIndex(0);
        assertTrue(manager.shouldRender(localUuid, CosmeticSlot.HAT));
        CosmeticsModule.get().showHats.set(false);
        assertFalse(manager.shouldRender(localUuid, CosmeticSlot.HAT));
        CosmeticsModule.get().showHats.set(true);
        assertTrue(manager.shouldRender(localUuid, CosmeticSlot.HAT));
    }

    @Test
    void testDiscordLinkCodeReward() {
        CosmeticsManager manager = CosmeticsManager.get();
        assertNull(manager.activeLinkCode());

        manager.setLinkCode("LADS-7788", 60);
        assertEquals("LADS-7788", manager.activeLinkCode());

        CosmeticItem discordCape = CosmeticLibrary.get("lads:cape_discord");
        assertNotNull(discordCape);
        assertTrue(discordCape.description().contains("Discord"));
    }

    @Test
    void testNullGuardShouldRender() {
        CosmeticsManager manager = CosmeticsManager.get();
        UUID uuid = UUID.randomUUID();
        // Regression test for Finding 1: passing null uuid or slot must return false without NPE
        assertFalse(manager.shouldRender(null, CosmeticSlot.HAT));
        assertFalse(manager.shouldRender(uuid, null));
        assertFalse(manager.shouldRender(null, null));
        assertNull(manager.getEquipped(null, CosmeticSlot.HAT));
        assertNull(manager.getEquipped(uuid, null));
    }
}
