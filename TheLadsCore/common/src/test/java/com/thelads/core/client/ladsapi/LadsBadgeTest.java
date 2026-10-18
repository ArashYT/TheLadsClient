package com.thelads.core.client.ladsapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.LadsBadgeModule;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LadsBadgeTest {
    private UUID myUuid;
    private UUID otherLadsUuid;
    private UUID nonLadsUuid;
    private LadsBadgeModule module;

    @BeforeEach
    void setUp() {
        myUuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        otherLadsUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        nonLadsUuid = UUID.fromString("99999999-8888-7777-6666-555555555555");

        LadsGameBridge.set(new DefaultGameBridge() {
            @Override
            public Session session() {
                return new Session(myUuid, "LocalPlayer", "token");
            }
        });

        LadsBadge.clearCacheForTest();
        LadsPresence.serverClear();
        LadsPresence.relayClear();

        module = (LadsBadgeModule) ModuleManager.getInstance().getModule(LadsBadgeModule.NAME);
        assertNotNull(module, "Lads Badge module must be registered");
        module.setEnabled(true);
        module.tab.set(true);
        module.nametag.set(true);
        module.showMine.set(true);

        LadsPresence.relayAdd(otherLadsUuid);
    }

    @AfterEach
    void tearDown() {
        LadsBadge.clearCacheForTest();
        LadsPresence.serverClear();
        LadsPresence.relayClear();
        LadsGameBridge.set(new DefaultGameBridge());
    }

    @Test
    void nullPlayerNeverShowsBadge() {
        assertFalse(LadsBadge.showInTab(null));
        assertFalse(LadsBadge.showOnNametag(null));
    }

    @Test
    void moduleDisabledHidesBadgeForAll() {
        module.setEnabled(false);
        assertFalse(LadsBadge.showInTab(myUuid));
        assertFalse(LadsBadge.showInTab(otherLadsUuid));
        assertFalse(LadsBadge.showInTab(nonLadsUuid));

        assertFalse(LadsBadge.showOnNametag(myUuid));
        assertFalse(LadsBadge.showOnNametag(otherLadsUuid));
        assertFalse(LadsBadge.showOnNametag(nonLadsUuid));
    }

    @Test
    void tabOptionDisabledHidesOnlyInTab() {
        module.tab.set(false);
        // In tab: all false
        assertFalse(LadsBadge.showInTab(myUuid));
        assertFalse(LadsBadge.showInTab(otherLadsUuid));
        assertFalse(LadsBadge.showInTab(nonLadsUuid));

        // On nametag: still active
        assertTrue(LadsBadge.showOnNametag(myUuid));
        assertTrue(LadsBadge.showOnNametag(otherLadsUuid));
        assertFalse(LadsBadge.showOnNametag(nonLadsUuid));
    }

    @Test
    void nametagOptionDisabledHidesOnlyOnNametag() {
        module.nametag.set(false);
        // On nametag: all false
        assertFalse(LadsBadge.showOnNametag(myUuid));
        assertFalse(LadsBadge.showOnNametag(otherLadsUuid));
        assertFalse(LadsBadge.showOnNametag(nonLadsUuid));

        // In tab: still active
        assertTrue(LadsBadge.showInTab(myUuid));
        assertTrue(LadsBadge.showInTab(otherLadsUuid));
        assertFalse(LadsBadge.showInTab(nonLadsUuid));
    }

    @Test
    void showMineOptionControlsLocalPlayerBadge() {
        // Show mine = true
        module.showMine.set(true);
        assertTrue(LadsBadge.showInTab(myUuid));
        assertTrue(LadsBadge.showOnNametag(myUuid));

        // Show mine = false
        module.showMine.set(false);
        assertFalse(LadsBadge.showInTab(myUuid));
        assertFalse(LadsBadge.showOnNametag(myUuid));

        // Even if local UUID is somehow added to presence, showMine=false must win
        LadsPresence.relayAdd(myUuid);
        assertFalse(LadsBadge.showInTab(myUuid));
        assertFalse(LadsBadge.showOnNametag(myUuid));

        // Other Lads player is unaffected by showMine
        assertTrue(LadsBadge.showInTab(otherLadsUuid));
        assertTrue(LadsBadge.showOnNametag(otherLadsUuid));
    }

    @Test
    void otherLadsAndNonLadsPresenceRules() {
        // Other Lads in relay presence shows badge
        assertTrue(LadsBadge.showInTab(otherLadsUuid));
        assertTrue(LadsBadge.showOnNametag(otherLadsUuid));

        // Non-Lads player does not show badge
        assertFalse(LadsBadge.showInTab(nonLadsUuid));
        assertFalse(LadsBadge.showOnNametag(nonLadsUuid));

        // Server presence also works
        UUID serverLadsUuid = UUID.randomUUID();
        assertFalse(LadsBadge.showInTab(serverLadsUuid));
        LadsPresence.serverAdd(java.util.List.of(serverLadsUuid));
        assertTrue(LadsBadge.showInTab(serverLadsUuid));
        assertTrue(LadsBadge.showOnNametag(serverLadsUuid));
    }

    @Test
    void badgeTextureResourceIsValid64x64Png() throws Exception {
        assertEquals("theladscore", LadsBadge.TEXTURE_NAMESPACE);
        assertEquals("textures/gui/lads_badge.png", LadsBadge.TEXTURE_PATH);
        assertEquals(64, LadsBadge.TEXTURE_SIZE);
        assertEquals(8, LadsBadge.TAB_SIZE);
        assertEquals(9, LadsBadge.NAMETAG_SIZE);
        assertEquals(1, LadsBadge.GAP);

        String path = "/assets/" + LadsBadge.TEXTURE_NAMESPACE + "/" + LadsBadge.TEXTURE_PATH;
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertNotNull(in, "Texture resource must exist at " + path);
            BufferedImage img = ImageIO.read(in);
            assertNotNull(img, "Texture must decode as valid image");
            assertEquals(64, img.getWidth());
            assertEquals(64, img.getHeight());
        }
    }

    @Test
    void allocationFreeHighFrequencySimulation() {
        // Simulate high-frequency frame queries (e.g. 100 tab rows x 500 frames)
        UUID[] players = new UUID[100];
        players[0] = myUuid;
        players[1] = otherLadsUuid;
        for (int i = 2; i < 100; i++) {
            players[i] = UUID.randomUUID();
        }

        int showCount = 0;
        for (int frame = 0; frame < 500; frame++) {
            for (UUID p : players) {
                if (LadsBadge.showInTab(p)) showCount++;
                if (LadsBadge.showOnNametag(p)) showCount++;
            }
        }
        // myUuid (2 per frame) + otherLadsUuid (2 per frame) = 4 per frame * 500 = 2000
        assertEquals(2000, showCount);
    }
}
