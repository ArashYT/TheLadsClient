package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.NickHiderModule;
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
            mod.hideOthersNames.set(false);
            mod.hideOwnName.set(true);
        }
        NickHider.clearOthers();
    }

    @Test
    void testNickHiderDefaults() {
        NickHiderModule module = new NickHiderModule();
        assertFalse(module.isEnabled(), "Nick Hider should default to disabled");
        assertTrue(module.hideOwnName.get());
        assertEquals("You", module.customNick.getValue());
        assertTrue(module.hideOwnSkin.get());
        assertFalse(module.hideOthersNames.get());
        assertEquals("Player", module.othersNick.getValue());
        assertFalse(module.hideOthersSkins.get());
    }

    @Test
    void testSkinHidingLogic() {
        UUID testUuid = UUID.randomUUID();
        assertFalse(NickHider.shouldHideSkin(testUuid, true));
        assertFalse(NickHider.shouldHideSkin(testUuid, false));
    }

    @Test
    void testOthersNameDisguise() {
        NickHiderModule mod = (NickHiderModule) ModuleManager.getInstance().getModule(NickHiderModule.NAME);
        assertNotNull(mod);
        mod.setEnabled(true);
        mod.hideOthersNames.set(true);
        mod.hideOwnName.set(true);

        NickHider.clearOthers();
        NickHider.registerOtherPlayer("Alice");
        NickHider.registerOtherPlayer("Bob");

        Map<String, String> renames = Nicknames.active("MyOwnAccount");
        assertEquals("You", renames.get("myownaccount"));
        assertEquals("Player", renames.get("alice"));
        assertEquals("Player", renames.get("bob"));

        String renamedChat = Nicknames.rename("<Alice> Hello MyOwnAccount and Bob!", renames);
        assertEquals("<Player> Hello You and Player!", renamedChat);
    }
}
