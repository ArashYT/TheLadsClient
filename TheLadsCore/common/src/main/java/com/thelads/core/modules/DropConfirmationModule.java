package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public final class DropConfirmationModule extends Module {
    public static final String NAME = "DropConfirmation";

    public final SliderOption timeoutMs;
    public final BoolOption enchantOnly;

    public DropConfirmationModule() {
        super(NAME, "Requires pressing the drop key twice to drop valuable or enchanted items, preventing accidental item loss.");
        timeoutMs = addOption(new SliderOption("Confirmation Window (ms)", 1500, 500, 3000, 250));
        enchantOnly = addOption(new BoolOption("Enchanted Items Only", false));
        setEnabled(false);
    }

    public long getTimeoutMillis() {
        return (long) timeoutMs.getValue();
    }

    public boolean isEnchantOnly() {
        return enchantOnly.get();
    }
}
