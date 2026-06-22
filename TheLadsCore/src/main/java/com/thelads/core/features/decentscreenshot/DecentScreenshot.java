/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ModInitializer
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.thelads.core.features.decentscreenshot;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecentScreenshot
implements ModInitializer {
    public static final String MOD_ID = "decentscreenshot";
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"decentscreenshot");

    public void onInitialize() {
        LOGGER.info("[DecentScreenshot] Initialized.");
    }
}

