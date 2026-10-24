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
        com.thelads.core.security.AntiInjectDetector.init();
        ConfigManager.load();
        com.thelads.core.client.util.LogPruner.pruneIfEnabled(new java.io.File("logs"));
        NativeKeyBindings1201.init();
        NativeFeatures1201.initialize();
        com.thelads.core.v1_20_1.render.Render1201.init();
        QaProbe1201.register(); // QA only: inert unless the Verification harness sets -Dthelads.verifyAutoWorld / -Dthelads.qaScript
        com.thelads.core.v1_20_1.feature.NativeAutohide1201.registerQaBarScreens(); // QA only: the QA progress bar on the screens outside a world
        com.thelads.core.v1_20_1.feature.Probe193Packs1201.register();
        com.thelads.core.v1_20_1.feature.SoundBreakProbe1201.register(); // QA only: LADS_QA_SOUNDBREAK=1
        com.thelads.core.v1_20_1.cosmetics.anim.EmoteAnim1201.register(); // emotes: key, wheel, stop rules (poses: emotes mixins); QA LADS_QA_EMOTE=1
        com.thelads.core.v1_20_1.feature.ParityProbe1201.register(); // QA only: LADS_QA_PARITY=1 (SurvivalTweaks, StackRefill)
        com.thelads.core.v1_20_1.input.InputLatency1201.init();
        com.thelads.core.v1_20_1.input.InputProbe1201.register();

        ExternalModSettings1201.register();
        com.thelads.core.v1_20_1.gui.ShaderToggleScreen1201.register(); // Iris only while shaders are on (the launcher suspends it)
        com.thelads.core.v1_20_1.feature.ebe.BakedBlockEntities1201.init();

        com.thelads.core.v1_20_1.feature.Menus1201.register();
                com.thelads.core.v1_20_1.feature.fastquit.FastQuitProbe1201.initialize();

        ModuleSupport.registerBuiltIn("Threads");
        ModuleSupport.registerBuiltIn(com.thelads.core.client.hud.ActionBar.MODULE); // OverlayGuiMixin, ActionBarHudElement
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
            "DamageIndicator", "JumpReset", "MediaPlayer", "HorseStats",
            "PlayerCounter", "TotemPopCounter", "BlockInfo", "TierTagger",
            "CustomText", "CustomShape", "PlayerHead", "BlockAbove",
            "EntityCounter", "ShaderPack", "HeldItemLore",
            "Subtitles", "TitleTweaker", "ToastControl", "VanillaHUD",
            // Gameplay & Visuals
            "Chat", "Crosshair Tweaks", "DynamicLights",
            "Custom FOV", "Paperdoll", "KillBanner", com.thelads.core.modules.ComboSoundsModule.NAME, com.thelads.core.client.DamageTilt.MODULE, "VerticalBobbing",
            com.thelads.core.modules.OldAnimationsModule.NAME, "Item Physics", "Nametags",
            "BetterScreenshots", "AutoReconnect", "IgnorePacketErrors", "SmoothHotbar",
            "BorderlessFullscreen", "DisableNarrator", "Raised", "FarBlockEntities",
            "SignalLoss", "Flashback Settings", "ShulkerBoxUtils",
            "TabList", "EnhancedTooltips", "EnhancedToolbars", "PingView",
            "Performance", "Exordium", "BetterResolution", "ClientTools",
            "ScalableLux", "ModernAdvancements", "Resourcify", "Minimap",
            // In-pack modular enhancements
            "DynamicFPS", "SkinLayers", "NotEnoughAnimations", "BetterF3", "BetterStats",
            "Clumps", "AppleSkin", "Jade", "MouseTweaks",
            "ConfirmDisconnect", "NumericalEnchantments", "LogPruner", "DropConfirmation", "ScrollTweaks", "AutoPerspective", "AutoBackup"
        );


        com.thelads.core.v1_20_1.game.Gameplay1201.register();
        com.thelads.core.v1_20_1.perf.Performance1201.register();
        com.thelads.core.v1_20_1.hud.Hud1201.register();
        com.thelads.core.v1_20_1.feature.Hypixel1201.register();
                com.thelads.core.v1_20_1.feature.ParticleCore1201.register();
        com.thelads.core.v1_20_1.world.World1201.register(); // Wavy Capes, TNT Timer
        com.thelads.core.v1_20_1.pvp.PvpVisuals1201.register(); // Color Saturation, Motion/Menu Blur, Hide Foliage, Clear Glass

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            com.thelads.core.client.bridge.LadsGameBridge.get().tickClient();
            NativeFeatures1201.tick();
            NativeDiscordPresence1201.tick();
            com.thelads.core.client.cosmetics.net.CosmeticsLink.tick();
            NativeKillBanner1201.tick();
            com.thelads.core.mods.CoreCatalogExporter.exportIfChanged();
        });

        LOGGER.info("TheLadsCore 1.20.1 initialized successfully.");
    }
}
