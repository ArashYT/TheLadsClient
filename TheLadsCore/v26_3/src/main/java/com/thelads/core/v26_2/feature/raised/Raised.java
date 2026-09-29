// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised;

import com.thelads.core.v26_2.feature.raised.config.Config;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Raised implements ModInitializer {

    public static final String MOD_ID = "raised";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void loadConfiguration() {
        LOGGER.info("Loading Raised config...");
        Config.load();
    }

    @Override
    public void onInitialize() {
        loadConfiguration();
    }

}
