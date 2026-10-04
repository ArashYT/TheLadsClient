package com.thelads.core.v26_2;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v26_2.adapter.VanillaGameBridge26;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheLadsCoreClient26 implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-26.2");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing TheLadsCore for Minecraft 26.2...");
        LadsGameBridge.set(new VanillaGameBridge26());
        com.thelads.core.v26_2.feature.paperdoll.PlayerActions.initialize();
        ConfigManager.load();
        com.thelads.core.v26_2.feature.LocalSkins.initialize();
        com.thelads.core.config.ModuleSupport.registerBuiltIn("Threads");
        // These HUD controls are consumed by HudManager through the required native HUD mixin.
        // Other modules remain unavailable until their native behavior and options are connected.
        ModuleSupport.registerBuiltIn("FPS", "Coordinates", "PingHUD", "Memory", "Speed",
            "Day", "Time", "XP", "Potion Effects", "Zoom", "ToggleSprint", "ToggleSneak",
            "Fullbright", "TitleScreen", "Title Scale", "CPS", "Keystrokes",
            "Biome", "ArmorHUD", "Direction", "Health", "Hunger", "Scoreboard", "TexturePacks");
        com.thelads.core.v26_2.gui.ExternalModSettings.register();
        com.thelads.core.v26_2.feature.NativeQualityOfLife.register();
        com.thelads.core.v26_2.feature.NativeChatHeads.register();
        com.thelads.core.v26_2.feature.AddServerProbe.register();
        com.thelads.core.v26_2.feature.Renderer134Probe.register();
        com.thelads.core.v26_2.feature.Version134ReplayProbe.register();
        com.thelads.core.v26_2.feature.NativeTooltips.initializeVerification();
        com.thelads.core.v26_2.feature.food.NativeFoodOverlay.initialize();
        com.thelads.core.v26_2.feature.paperdoll.NativePaperDoll.register();
        com.thelads.core.v26_2.feature.raised.NativeRaised.initialize();
        com.thelads.core.v26_2.feature.tabtweaks.NativeTabTweaks.initialize();
        com.thelads.core.v26_2.feature.screenshots.ScreenshotViewer.register();
        com.thelads.core.v26_2.feature.clumps.NativeClumps.initialize();
        com.thelads.core.v26_2.feature.GoodMcAttackSpeedReset.register();
        com.thelads.core.v26_2.embedded.EmbeddedMods.clientInit();
        LOGGER.info("TheLadsCore 26.2 initialized successfully.");
        com.thelads.core.mods.NativeCatalogProbe.log();
        // The launcher lists Lads modules from this catalog; late registrations (Minimap) bump the revision on a later tick.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
            client -> com.thelads.core.mods.CoreCatalogExporter.exportIfChanged());
    }
}
