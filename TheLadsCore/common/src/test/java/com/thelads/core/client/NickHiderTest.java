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
            mod.hideOwnName.set(true);
            mod.hideOwnSkin.set(true);
        }
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
}
