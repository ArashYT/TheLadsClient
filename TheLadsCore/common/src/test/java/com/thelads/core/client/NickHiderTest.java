package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.NickHiderModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NickHiderTest {
    @AfterEach
    void tearDown() {
        NickHiderModule mod = (NickHiderModule) ModuleManager.getInstance().getModule(NickHiderModule.NAME);
        if (mod != null) {
            mod.setEnabled(false);
            mod.hideOwnName.set(true);
            mod.hideOthersNames.set(false);
            mod.hideOwnSkin.set(true);
        }
        Nicknames.tick();
    }

    @Test
    void testNickHiderDefaults() {
        NickHiderModule module = new NickHiderModule();
        assertFalse(module.isEnabled(), "Nick Hider should default to disabled");
        assertTrue(module.hideOwnName.get());
        assertEquals("You", module.customNick.getValue());
        assertTrue(module.hideOwnSkin.get());
        assertFalse(module.hideOthersSkins.get());
    }

    @Test
    void testSkinHidingLogic() {
        NickHiderModule mod = (NickHiderModule) ModuleManager.getInstance().getModule(NickHiderModule.NAME);
        assertNotNull(mod);
        UUID testUuid = UUID.randomUUID();

        mod.setEnabled(false);
        assertFalse(NickHider.shouldHideSkin(testUuid, true));
        assertFalse(NickHider.shouldHideSkin(testUuid, false));

        mod.setEnabled(true);
        mod.hideOwnSkin.set(true);
        mod.hideOthersSkins.set(false);
        assertTrue(NickHider.shouldHideSkin(testUuid, true));
        assertFalse(NickHider.shouldHideSkin(testUuid, false));

        mod.hideOthersSkins.set(true);
        assertTrue(NickHider.shouldHideSkin(testUuid, false));
    }

    @Test
    void testOwnNameDisguise() {
        NickHiderModule mod = (NickHiderModule) ModuleManager.getInstance().getModule(NickHiderModule.NAME);
        assertNotNull(mod);
        mod.setEnabled(true);
        mod.hideOwnName.set(true);
        mod.customNick.setValue("Streamer");

        Map<String, String> renames = Nicknames.active("MyOwnAccount");
        assertEquals("Streamer", renames.get("myownaccount"));

        String renamedChat = Nicknames.rename("<MyOwnAccount> Hello World!", renames);
        assertEquals("<Streamer> Hello World!", renamedChat);
    }

    @Test
    void testOthersNameDisguise() {
        NickHiderModule mod = (NickHiderModule) ModuleManager.getInstance().getModule(NickHiderModule.NAME);
        assertNotNull(mod);
        mod.setEnabled(true);
        mod.hideOthersNames.set(true);
        mod.othersNick.setValue("DisguisedPlayer");

        assertTrue(NickHider.isHideOthersNames());
        assertEquals("DisguisedPlayer", NickHider.getOthersNick());
    }

    @Test
    void testPlayerIdentityChangeWithConstantCount() {
        NickHiderModule mod = (NickHiderModule) ModuleManager.getInstance().getModule(NickHiderModule.NAME);
        assertNotNull(mod);
        mod.setEnabled(true);
        mod.hideOthersNames.set(true);
        mod.othersNick.setValue("DisguisedPlayer");

        com.thelads.core.client.bridge.LadsGameBridge originalBridge = com.thelads.core.client.bridge.LadsGameBridge.get();
        final List<String> online = new ArrayList<>(List.of("Alice"));
        try {
            com.thelads.core.client.bridge.LadsGameBridge.set(new com.thelads.core.client.bridge.DefaultGameBridge() {
                @Override
                public List<String> getOnlinePlayerNames() {
                    return online;
                }
            });

            Nicknames.tick();
            Map<String, String> active1 = Nicknames.active("MyAccount");
            assertEquals("DisguisedPlayer", active1.get("alice"));
            assertNull(active1.get("bob"));

            // Player A leaves, Player B joins -> count remains 1
            online.clear();
            online.add("Bob");
            Nicknames.tick();

            Map<String, String> active2 = Nicknames.active("MyAccount");
            assertEquals("DisguisedPlayer", active2.get("bob"));
            assertNull(active2.get("alice"));
        } finally {
            com.thelads.core.client.bridge.LadsGameBridge.set(originalBridge);
        }
    }
}
