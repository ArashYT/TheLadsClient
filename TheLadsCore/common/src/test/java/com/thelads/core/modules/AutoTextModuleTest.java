package com.thelads.core.modules;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AutoTextModuleTest {
    @AfterEach void restore() {
        AutoTextModule module = AutoTextModule.get();
        for (var option : module.getOptions()) option.reset();
        module.setEnabled(false);
    }

    @Test void sendsTypedMessagesAtMostOnceASecond() {
        AutoTextModule module = AutoTextModule.get();
        module.messages[0].setValue("  /party warp ");
        assertNull(module.pressed(0, 5_000), "off by default (gated)");
        module.setEnabled(true);
        assertEquals("/party warp", module.pressed(0, 5_000));
        assertNull(module.pressed(0, 5_500));
        assertNull(module.pressed(1, 7_000), "an empty message sends nothing");
        assertEquals("/party warp", module.pressed(0, 7_000));
        assertNull(module.pressed(AutoTextModule.SLOTS, 9_000));
    }
}
