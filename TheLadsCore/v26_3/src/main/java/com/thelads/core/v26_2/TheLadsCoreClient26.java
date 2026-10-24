package com.thelads.core.v26_2;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v26_2.adapter.VanillaGameBridge26;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheLadsCoreClient26 implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-26.3");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing TheLadsCore for Minecraft 26.3...");
        LadsGameBridge.set(new VanillaGameBridge26());
        com.thelads.core.security.AntiInjectDetector.init();
        com.thelads.core.v26_2.feature.paperdoll.PlayerActions.initialize();
        ConfigManager.load();
        com.thelads.core.client.util.LogPruner.pruneIfEnabled(new java.io.File("logs"));
        com.thelads.core.v26_2.render.Render26.init();
        com.thelads.core.v26_2.feature.LocalSkins.initialize();
        com.thelads.core.config.ModuleSupport.registerBuiltIn("Threads");
        ModuleSupport.registerBuiltInRestart("Jasione");
        // These HUD controls are consumed by HudManager through the required native HUD mixin.
        // Other modules remain unavailable until their native behavior and options are connected.
        ModuleSupport.registerBuiltIn("FPS", "Coordinates", "PingHUD", "Memory", "Speed",
            "Day", "Time", "XP", "Potion Effects", "Zoom", com.thelads.core.modules.ToggleSprintModule.NAME,
            "Fullbright", "TitleScreen", "Title Scale", "CPS", "Keystrokes",
            "Biome", "ArmorHUD", "Direction", "Health", "Hunger", "Scoreboard", "TexturePacks",
            "ConfirmDisconnect", "NumericalEnchantments", "LogPruner", "DropConfirmation", "ScrollTweaks", "AutoPerspective", "AutoBackup");
        // HudManager's parity widgets and Inventory HUD; Nick Hider through LocalSkinMixin (skins) and Nicknames (names).
        ModuleSupport.registerBuiltIn(com.thelads.core.client.hud.HudManager.PARITY_HUDS);
        ModuleSupport.registerBuiltIn(com.thelads.core.modules.NickHiderModule.NAME);
        ModuleSupport.registerUnavailable(com.thelads.core.modules.ContainerQoLModule.NAME, "Only for Minecraft 1.8.9, as PolyPatcher's "
            + "click outside to close; it is not hooked into this version's container screens.");
        com.thelads.core.v26_2.gui.ExternalModSettings.register();
        com.thelads.core.v26_2.gui.ShaderToggleScreen26.register();
        com.thelads.core.v26_2.feature.ShaderToggleQa26.register(); // QA only: inert without LADS_QA_SHADERTOGGLE
        com.thelads.core.v26_2.feature.NativeAutohide.registerQaBarScreens(); // QA only: the QA progress bar on the screens outside a world
        com.thelads.core.v26_2.feature.NativeQualityOfLife.register();
        com.thelads.core.v26_2.feature.pvp.PvpVisuals26.register();
        com.thelads.core.v26_2.pvp.PvpVisuals26.register(); // Color Saturation, Motion/Menu Blur, Hide Foliage, Clear Glass
        com.thelads.core.v26_2.feature.ChatTweaks26.register(); // parity-1023 chat lane: chat and inventory tweaks, their keys
        com.thelads.core.v26_2.feature.NativeParticleCore.register();
        com.thelads.core.v26_2.feature.NativeFeatures.initialize();
        com.thelads.core.v26_2.feature.PvpView26.register(); // Freelook, ViewModel, Time/Weather Changer, Fog Customizer
        com.thelads.core.v26_2.hudextras.HudExtras26.register(); // HUD widgets and vanilla HUD tweaks (parity 1.9.19)
        com.thelads.core.v26_2.feature.NativeChatHeads.register();
        com.thelads.core.v26_2.feature.NativeBetterF3.register();
        com.thelads.core.v26_2.feature.NativeCustomFov.register();
        com.thelads.core.v26_2.feature.AddServerProbe.register();
        com.thelads.core.v26_2.feature.Probe193Packs26.register();
        com.thelads.core.v26_2.feature.Renderer134Probe.register();
        com.thelads.core.v26_2.feature.Version134ReplayProbe.register();
        com.thelads.core.v26_2.feature.flashback.NativeFlashback.initialize();
        if (Boolean.getBoolean("thelads.verifyFlashback")) com.thelads.core.v26_2.feature.FlashbackExportProbe.register();
        com.thelads.core.v26_2.feature.NativeTooltips.initializeVerification();
        com.thelads.core.v26_2.feature.HudInfoCapture.register();
        com.thelads.core.v26_2.feature.food.NativeFood.initialize();
        com.thelads.core.v26_2.feature.paperdoll.PaperDoll26.register();
        com.thelads.core.v26_2.feature.tabtweaks.NativeTabTweaks.initialize();
        com.thelads.core.v26_2.feature.NativeHypixel.initialize();
        com.thelads.core.v26_2.feature.screenshots.ScreenshotViewer.register();
        com.thelads.core.v26_2.feature.clumps.NativeClumps.initialize();
        com.thelads.core.v26_2.feature.NativeItemPhysics.initialize();
        com.thelads.core.v26_2.world.World26.register(); // Wavy Capes, TNT Timer
        com.thelads.core.v26_2.feature.NativeDynamicLights.initialize();
        com.thelads.core.v26_2.feature.async.AsyncTicking.initialize();
        com.thelads.core.v26_2.feature.GoodMcAttackSpeedReset.register();
        com.thelads.core.v26_2.feature.NativeCheats.register();
        com.thelads.core.v26_2.cosmetics.anim.EmoteAnim26.register(); // emotes: key, wheel, stop rules (poses: emotes mixins)
        com.thelads.core.v26_2.cosmetics.anim.ShowcaseEmote26.register(); // LADS_SHOWCASE_EMOTE only: the recorded emote showcase
        com.thelads.core.v26_2.showcase.ShowcaseDirector26.register(); // LADS_SHOWCASE_DIRECTOR only: scripted showcase director
        com.thelads.core.v26_2.embedded.EmbeddedMods.clientInit();


        com.thelads.core.v26_2.input.InputLatency26.init();
        com.thelads.core.v26_2.input.InputProbe26.register();
                com.thelads.core.v26_2.feature.fastquit.FastQuitProbe26.initialize();
                com.thelads.core.v26_2.feature.ebe.BakedBlockEntities263.init();
        LOGGER.info("TheLadsCore 26.3 initialized successfully.");
        com.thelads.core.mods.NativeCatalogProbe.log();
        // The launcher lists Lads modules from this catalog; late registrations (Minimap) bump the revision on a later tick.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            com.thelads.core.client.bridge.LadsGameBridge.get().tickClient();
            com.thelads.core.mods.CoreCatalogExporter.exportIfChanged();
            boolean loaded = client.isGameLoadFinished() && client.gui.overlay() == null;
            if (loaded) com.thelads.core.v26_2.feature.EnumValuesHook.reportOnce();
            com.thelads.core.shared.LogProbe.tick(loaded, client::stop);
        });
    }
}
