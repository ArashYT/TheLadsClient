package com.thelads.core.client.renderscale;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenderScale
implements ModInitializer {
    public static final String MOD_ID = "render-scale";
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"render-scale");

    public void onInitialize() {
        LOGGER.info("Hello Fabric world!");
    }
}
