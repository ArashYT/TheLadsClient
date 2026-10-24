package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.CreativeFlyBoostModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreativeFlyBoostTest {
    @Test
    void defaultsAndGating() {
        CreativeFlyBoostModule module = new CreativeFlyBoostModule();
        assertEquals("Creative Fly Boost", module.getName());
        assertFalse(module.isEnabled(), "Gated module must default to OFF");
        assertEquals(2.0, module.multiplier.getValue(), 0.001);
    }

    @Test
    void multiplierWhenDisabled() {
        // Module disabled returns 1.0F base
        assertEquals(1.0F, CreativeFlyBoost.getMultiplier(), 0.001F);
    }
}
