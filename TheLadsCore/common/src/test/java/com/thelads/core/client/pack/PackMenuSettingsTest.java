package com.thelads.core.client.pack;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.OldAnimationsModule;
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
    @Test void lowFireAndShieldButtons() {
        OldAnimationsModule anims = (OldAnimationsModule) ModuleManager.getInstance().getModule(OldAnimationsModule.NAME);
        BoolOption fire = anims.option(OldAnimationsModule.Feature.LOW_FIRE);
        boolean enabled = anims.isEnabled();
        try {
            anims.setEnabled(true);
            fire.set(true);
            assertEquals("Low Fire: ON", PackMenuSettings.overlayLabel(OldAnimationsModule.Feature.LOW_FIRE));
            fire.set(false);
            assertEquals("Low Fire: OFF", PackMenuSettings.overlayLabel(OldAnimationsModule.Feature.LOW_FIRE));
            fire.set(true);
            anims.setEnabled(false);
            assertFalse(PackMenuSettings.overlayAvailable());
            assertEquals("Low Fire: OFF", PackMenuSettings.overlayLabel(OldAnimationsModule.Feature.LOW_FIRE), "1.7 Animations off: nothing is lowered");
        } finally {
            anims.setEnabled(enabled);
            fire.reset();
        }
    }
}
