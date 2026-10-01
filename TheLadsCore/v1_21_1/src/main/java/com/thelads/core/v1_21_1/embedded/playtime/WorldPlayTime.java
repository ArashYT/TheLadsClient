// Adapted from World Play Time 1.2.2 by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.playtime;

import com.thelads.core.v1_21_1.embedded.playtime.config.ServerPlayTimeManager;
import com.thelads.core.v1_21_1.embedded.playtime.config.WPTConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldPlayTime {
    public static final String MOD_ID = "worldplaytime";
    public static final Logger LOGGER = LoggerFactory.getLogger("WorldPlayTime");

    public static void init() {
        WPTConfig.init();
        ServerPlayTimeManager.load();
    }
}
