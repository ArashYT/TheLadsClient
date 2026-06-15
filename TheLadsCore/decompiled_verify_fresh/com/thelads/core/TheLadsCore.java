/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ModInitializer
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.thelads.core;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheLadsCore
implements ModInitializer {
    public static final String MOD_ID = "theladscore";
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"theladscore");

    public void onInitialize() {
        LOGGER.info("The Lads Core Mod initialized!");
    }
}

