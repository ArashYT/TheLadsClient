package com.thelads.core.v1_20_1;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.adapter.VanillaGameBridge1201;
import com.thelads.core.v1_20_1.feature.NativeDiscordPresence1201;
import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import com.thelads.core.v1_20_1.feature.NativeKeyBindings1201;
import com.thelads.core.v1_20_1.feature.NativeKillBanner1201;
import com.thelads.core.v1_20_1.feature.QaProbe1201;
import com.thelads.core.v1_20_1.gui.ExternalModSettings1201;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheLadsCoreClient1201 implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing TheLadsCore for Minecraft 1.20.1...");
        LadsGameBridge.set(new VanillaGameBridge1201());
        ConfigManager.load();
        NativeKeyBindings1201.init();
        NativeFeatures1201.initialize();
        QaProbe1201.register(); // QA only: inert unless the Verification harness sets -Dthelads.verifyAutoWorld / -Dthelads.qaScript
        com.thelads.core.v1_20_1.input.InputLatency1201.init();
        com.thelads.core.v1_20_1.input.InputProbe1201.register();

        ExternalModSettings1201.register();

        ModuleSupport.registerBuiltIn("Threads");
        ModuleSupport.registerBuiltInRestart("Jasione");
        ModuleSupport.registerBuiltIn(
            // HUD Modules
            "FPS", "Coordinates", "PingHUD", "Memory", "Speed",
            "Day", "Time", "XP", "Potion Effects", "Zoom", com.thelads.core.modules.ToggleSprintModule.NAME,
            "Fullbright", "TitleScreen", "Title Scale", "CPS", "Keystrokes",
            "Biome", "ArmorHUD", "Direction", "Health", "Hunger", "Scoreboard", "TexturePacks",
            "Autohide", "BossBar", "RawInput", "LegacySwing", "DiscordRPC",
            "Clock", "Stopwatch", "ItemCounter", "ReachDisplay", "ServerAddress", "PortalCoordinates",
            "Voice Chat", "Voice Chat Group",
            // Gameplay & Visuals
            "Chat", com.thelads.core.client.ChatHeads.NAME, "Crosshair Tweaks", "DynamicLights",
            "Custom FOV", "Paperdoll", "KillBanner", com.thelads.core.client.DamageTilt.MODULE, "VerticalBobbing",
            com.thelads.core.modules.OldAnimationsModule.NAME, "Item Physics", "Nametags",
            "BetterScreenshots", "AutoReconnect", "IgnorePacketErrors", "SmoothHotbar",
            "BorderlessFullscreen", "DisableNarrator", "Raised", "FarBlockEntities",
            "ParticleBudget", "SignalLoss", "Flashback Settings", "ShulkerBoxUtils",
            "TabList", "EnhancedTooltips", "EnhancedToolbars", "PingView",
            "Performance", "Exordium", "BetterResolution", "ClientTools",
            "ScalableLux", "ModernAdvancements", "Resourcify", "Minimap",
            // In-pack modular enhancements
            "DynamicFPS", "SkinLayers", "NotEnoughAnimations", "BetterF3", "BetterStats",
            "Clumps", "AppleSkin", "Jade", "MouseTweaks"
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            NativeFeatures1201.tick();
            NativeDiscordPresence1201.tick();
            NativeKillBanner1201.tick();
            com.thelads.core.mods.CoreCatalogExporter.exportIfChanged();
        });

        LOGGER.info("TheLadsCore 1.20.1 initialized successfully.");
    }
}
