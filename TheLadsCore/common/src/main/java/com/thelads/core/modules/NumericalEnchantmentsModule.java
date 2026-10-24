package com.thelads.core.modules;

import com.thelads.core.config.Module;

public final class NumericalEnchantmentsModule extends Module {
    public static final String NAME = "NumericalEnchantments";

    public NumericalEnchantmentsModule() {
        super(NAME, "Replaces Roman numerals (I, V, X) on enchantment books and potion effects with Arabic numbers (1, 5, 10).");
        setEnabled(false);
    }
}
