package com.thelads.core.config;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ModuleOptionLookupTest {
    @Test void findsOptionsByNameAndSeesLaterOnes() {
        Module module = new Module("Sample", "x");
        BoolOption first = module.addOption(new BoolOption("A", true));
        assertSame(first, module.getOption("A"));
        assertNull(module.getOption("B"));
        BoolOption second = module.addOption(new BoolOption("B", false));
        assertSame(second, module.getOption("B"), "an option added after a lookup is found");
        module.addOption(new BoolOption("A", false));
        assertSame(first, module.getOption("A"), "the first of equal names wins, as a scan found it");
        assertNull(module.getOption(null));
    }
}
