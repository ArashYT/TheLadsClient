package com.thelads.core.client;

import com.thelads.core.modules.NickHiderModule;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NickHiderTest {
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
        // Without active module registered or enabled, skin hiding is false
        assertFalse(NickHider.shouldHideSkin(testUuid, true));
        assertFalse(NickHider.shouldHideSkin(testUuid, false));
    }
}
