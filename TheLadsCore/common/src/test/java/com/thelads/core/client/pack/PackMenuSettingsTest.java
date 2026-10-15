package com.thelads.core.client.pack;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.Test;

class PackMenuSettingsTest {
    @Test void incompatiblePackOptions() {
        Module m = ModuleManager.getInstance().getModule(PackMenuSettings.MODULE_NAME);
        assertFalse(PackMenuSettings.ignoreIncompatible(), "hiding wrong-version packs is opt-in");
        assertTrue(PackMenuSettings.noCompatCheck(), "no warning by default, as before");
        BoolOption ignore = (BoolOption) m.getOption(PackMenuSettings.OPTION_IGNORE_INCOMPATIBLE);
        BoolOption check = (BoolOption) m.getOption(PackMenuSettings.OPTION_NO_COMPAT_CHECK);
        ignore.set(true);
        check.set(false);
        try {
            assertTrue(PackMenuSettings.ignoreIncompatible());
            assertFalse(PackMenuSettings.noCompatCheck());
        } finally {
            ignore.reset();
            check.reset();
        }
    }
}
