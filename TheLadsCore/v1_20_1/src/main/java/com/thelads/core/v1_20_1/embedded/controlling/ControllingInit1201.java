package com.thelads.core.v1_20_1.embedded.controlling;

import com.thelads.core.v1_20_1.embedded.controlling.client.NewKeyBindsScreen;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controlling for 1.20.1, embedded in Lads Core: the key binds screen with search and auto-complete (Searchables), the free
 * keys view, show-unbound/conflict filters and sort orders. Called once at client init, only while the original Controlling
 * jar is not installed ({@code EmbeddedMods1201}); the screen swap itself is {@code ControllingScreenMixin}.
 */
public final class ControllingInit1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-Controlling");

    private ControllingInit1201() {}

    public static void init() {
        LOGGER.info("Controlling (embedded): the key binds screen has search, free keys, filters and sort");
    }

    public static Screen createScreen(Screen parent, Options options) {
        return new NewKeyBindsScreen(parent, options);
    }
}
