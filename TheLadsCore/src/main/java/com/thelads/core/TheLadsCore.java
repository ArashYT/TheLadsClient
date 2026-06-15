package com.thelads.core;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheLadsCore implements ModInitializer {
	public static final String MOD_ID = "theladscore";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("The Lads Core Mod initialized!");
	}
}
