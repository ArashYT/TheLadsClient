package com.thelads.core;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheLadsCore implements ModInitializer {
	public static final String MOD_ID = "theladscore";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("The Lads Core Mod initialized!");

		// Initialize Client Sort always-on feature
		new com.thelads.core.features.alwayson.clientsort.ClientSortFabric().onInitialize();

		// Setup standard Fabric command registration for /statistics (and /stats alias)
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			com.thelads.core.features.alwayson.betterstatisticscreen.command.StatisticsCommand.register(dispatcher, registryAccess);
		});

		// If dedicated server environment, instantiate BetterStatsServer to load config
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
			new com.thelads.core.features.alwayson.betterstatisticscreen.server.BetterStatsServer();
		}

		// HyperLaunch — sets up parallel bootstrap thread pools
		com.thelads.core.features.alwayson.hyperlaunch.HyperLaunch.init();

		// LetMeDespawn — loads despawn config
		new com.thelads.core.features.alwayson.letmedespawn.LetMeDespawn().onInitialize();

		// VMP (Very Many Players) — server-side chunk/entity-tracking optimizations
		new com.thelads.core.features.alwayson.vmp.VMPMod().onInitialize();

		// AppleSkin — registers networking payload types on the common side
		new squeek.appleskin.AppleSkinCommon().onInitialize();

		// Just Enough Items — registers JEI plugin system on the common side
		new mezz.jei.fabric.JustEnoughItems().onInitialize();


		// Quick Pack — server-side item packing logic
		new me.drex.quickpack.fabric.QuickPackFabric().onInitialize();

		// Passive Shield — registers passive shield behaviour
		new com.natamus.passiveshield.ModFabric().onInitialize();

		// Immersive Hotbar — disabled for 26.2 (Gui→Hud rename breaks mixins)
		// new derp.immersivehotbar.ImmersiveHotbar().onInitialize();



		// Entity View Distance — registers server-side entity distance config
		new eu.pb4.entityviewdistance.EVDMod().onInitialize();

		// Resource Pack Options (respackopts) — registers pack config system
		new dev.jfronny.respackopts.platform.fabric.RespackoptsFabric().onInitialize();



		// Modern Advancements — server-side networking, advancement tracking, optional HTTP API
		new com.thelads.core.features.auto.modernadvancements.ModernAdvancements().onInitialize();
	}
}

