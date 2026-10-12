package com.thelads.core.v1_20_1.feature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the menus lane (title, pause, loading, settings, multiplayer, reconnect, screenshots): registers everything
 * the menu screens need at client start. Called by one line from TheLadsCoreClient1201.
 */
public final class Menus1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1");

    private Menus1201() {}

    public static void register() {
        LocalSkins1201.initialize();
        NativeReconnect1201.register();
        com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewer1201.register();
        MenusQa1201.register();
        UiQa1201.register();
        KillBannerQa1201.register();
        HudLayoutQa1201.register();
        SoundsQa1201.register();
        FixesQa1201.register();
        LOGGER.info("Lads menus (title, pause, loading, settings) registered for 1.20.1");
    }
}
