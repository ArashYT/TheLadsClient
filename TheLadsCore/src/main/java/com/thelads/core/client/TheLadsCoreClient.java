package com.thelads.core.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ProfileManager;
import com.thelads.core.modules.FullbrightModule;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.modules.ToggleSneakModule;
import com.thelads.core.modules.ZoomModule;
import com.thelads.core.config.HudSettings;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.auth.AccountSwitcherScreen;
import com.thelads.core.client.LadsProfileSync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.thelads.core.client.capes.Capes;

@Environment(EnvType.CLIENT)
public class TheLadsCoreClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("modid");
    public static KeyMapping settingsKeyBind;
    public static KeyMapping hudKeyBind;
    public static KeyMapping authKeyBind;
    public static KeyMapping zoomKeyBind;
    public static KeyMapping nametagsKeyBind;
    public static KeyMapping toggleSneakKeyBind;
    public static KeyMapping toggleSprintKeyBind;
    public static KeyMapping worldMapKeyBind;
    private static boolean earlyWindowChecked = false;

    @Override
    public void onInitializeClient() {
        setupOptimizations();
        LOGGER.info("The Lads Core Client initialized!");
        com.thelads.core.features.auto.shulkerboxutils.ShulkerBoxUtils.initClient();
        Capes.INSTANCE.onInitializeClient();

        // Initialize Better Stats Screen client
        com.thelads.core.features.alwayson.betterstatisticscreen.client.BetterStatsClient.init();

        // Initialize Client Sort always-on client feature
        new com.thelads.core.features.alwayson.clientsort.ClientSortClientFabric().onInitializeClient();

        // ImmediatelyFast — early config init (onRenderSystemInit called via its MixinMinecraft)
        com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast.earlyInit();

        // Advancements Reloaded — loads mod config

        // Entity Culling — registers tick event for culling
        new com.thelads.core.features.alwayson.entityculling.EntityCullingMod().onInitializeClient();

        // Raise Sound Limit — loads sound config and injector
        new com.thelads.core.features.alwayson.raisesoundlimit.RSLSMod().onInitializeClient();

        // 3D Skin Layers — initializes skin layer rendering
        new com.thelads.core.features.alwayson.skinlayers.SkinLayersMod().onInitializeClient();

        // Raised — disabled for 26.2 (26.1.1 jar; our RaisedMixin.java covers Hud.class behavior)
        // new dev.yurisuika.raised.RaisedClient().onInitializeClient();

        // Passive Shield — client-side shield rendering
        new com.natamus.passiveshield.ModFabricClient().onInitializeClient();

        // Immersive Hotbar — disabled for 26.2 (Gui→Hud rename breaks mixins, no 26.2 version)
        // new derp.immersivehotbar.ImmersiveHotbarClient().onInitializeClient();

        // ScreenFX — client-side screen effect rendering
        new com.laryisland.screenfx.ScreenFX().onInitializeClient();

        // Fancy Door Animations — disabled entrypoint for 26.2 (26.1.x jar; mixins still active)
        // new io.github.yxmna.fancydooranim.FancyDoorAnimClient().onInitializeClient();

        // Threads — client-side thread display
        // new com.threads.Threads().onInitializeClient();

        // WaveyCapes — animated cape rendering
        new dev.tr7zw.waveycapes.WaveyCapesMod().onInitializeClient();

        // AppleSkin — client-side food saturation HUD
        new squeek.appleskin.AppleSkin().onInitializeClient();

        // Entity View Distance — client-side entity distance slider
        new eu.pb4.entityviewdistance.EVDMod().onInitializeClient();

        // Not Enough Animations — player animation improvements
        new dev.tr7zw.notenoughanimations.NEAnimationsMod().onInitializeClient();

        // Client Tweaks — various client-side tweaks
        new net.blay09.mods.clienttweaks.fabric.client.FabricClientTweaksClient().onInitializeClient();

        // Cursors Extended — custom cursor support
        new io.github.fishstiz.cursors_extended.CursorsExtendedFabric().onInitializeClient();

        // Ixeris — UI improvements (DISABLED: Causes native JVM OpenGL crash on 26.2)
        // new me.decce.ixeris.fabric.IxerisModFabric().onInitializeClient();

        // Resource Pack Options — client-side pack config
        new dev.jfronny.respackopts.platform.fabric.RespackoptsClientFabric().onInitializeClient();

        // Enhanced Tooltips — richer item tooltip rendering
        new dev.ultimatchamp.enhancedtooltips.loaders.EnhancedTooltipsFabric().onInitializeClient();

        // Extreme Sound Muffler — per-sound volume control
        new com.leobeliik.extremesoundmuffler.SoundMufflerFabric().onInitializeClient();



        // Modern Advancements — custom advancement screen, HUD tracker, keybinds, client networking
        new com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient().onInitializeClient();

        ConfigManager.load();
        ProfileManager.get().load();

        // Apply launcher profile (lads_profile.json) to Minecraft session on startup
        LadsProfileSync.read();

        // Apply any saved HUD element positions from config
        for (HudElement el : HudManager.getInstance().getElements()) {
            int[] pos = HudSettings.getInstance().getPosition(el.getModuleName());
            if (pos != null) {
                el.setPosition(pos[0], pos[1]);
            }
        }

        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess) -> {
                Module mod = ModuleManager.getInstance().getModule("KillBanner");
                if (mod instanceof com.thelads.core.modules.KillBannerModule) {
                    new com.thelads.core.commands.KillBannerCommand((com.thelads.core.modules.KillBannerModule) mod).register(dispatcher);
                }
            }
        );

        ModSyncNetworking.register();

        settingsKeyBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.thelads.settings",
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            net.minecraft.client.KeyMapping.Category.MISC
        ));

        hudKeyBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.thelads.hud",
            GLFW.GLFW_KEY_RIGHT_CONTROL,
            net.minecraft.client.KeyMapping.Category.MISC
        ));

        authKeyBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.thelads.auth",
            GLFW.GLFW_KEY_RIGHT_ALT,
            net.minecraft.client.KeyMapping.Category.MISC
        ));

        zoomKeyBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.thelads.zoom",
            GLFW.GLFW_KEY_C,
            net.minecraft.client.KeyMapping.Category.MISC
        ));

        nametagsKeyBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.thelads.nametags",
            GLFW.GLFW_KEY_H,
            net.minecraft.client.KeyMapping.Category.MISC
        ));

        // Unbound by default — bind in Options > Controls. These TOGGLE sneak/sprint;
        // the vanilla Sneak/Sprint keys remain as hold-to-sneak/sprint.
        toggleSneakKeyBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.thelads.togglesneak",
            GLFW.GLFW_KEY_UNKNOWN,
            net.minecraft.client.KeyMapping.Category.MISC
        ));
        toggleSprintKeyBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.thelads.togglesprint",
            GLFW.GLFW_KEY_UNKNOWN,
            net.minecraft.client.KeyMapping.Category.MISC
        ));

        worldMapKeyBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.thelads.worldmap",
            GLFW.GLFW_KEY_M,
            net.minecraft.client.KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Last-resort cleanup: if the preLaunch loading window still wasn't
            // adopted by the time the game ticks, don't let it linger.
            if (!earlyWindowChecked) {
                earlyWindowChecked = true;
                LadsEarlyWindow.abandonIfUnused();
                // Restore equipped cosmetics persisted from a previous session
                com.thelads.core.client.cosmetics.backend.CosmeticsBackend.load();
            }

            while (settingsKeyBind.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.setScreenAndShow(new LadsSettingsScreen(null));
                }
            }
            while (hudKeyBind.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.setScreenAndShow(new DraggableHudScreen());
                }
            }
            while (authKeyBind.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.setScreenAndShow(new AccountSwitcherScreen(null));
                }
            }
            while (nametagsKeyBind.consumeClick()) {
                Module nt = ModuleManager.getInstance().getModule("Nametags");
                if (nt != null) {
                    nt.toggle();
                    ConfigManager.save();
                    if (client.player != null) {
                        client.player.sendOverlayMessage(
                            net.minecraft.network.chat.Component.literal(
                                nt.isEnabled() ? "Nametags hidden" : "Nametags shown"));
                    }
                }
            }

            while (toggleSneakKeyBind.consumeClick()) {
                Module m = ModuleManager.getInstance().getModule("ToggleSneak");
                if (m instanceof ToggleSneakModule tsn2 && tsn2.isEnabled()) {
                    tsn2.onToggleKey();
                    if (client.player != null)
                        client.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                            tsn2.isToggled() ? "Sneak: ON" : "Sneak: OFF"));
                }
            }
            while (toggleSprintKeyBind.consumeClick()) {
                Module m = ModuleManager.getInstance().getModule("ToggleSprint");
                if (m instanceof ToggleSprintModule tsp2 && tsp2.isEnabled()) {
                    tsp2.onToggleKey();
                    if (client.player != null)
                        client.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                            tsp2.isToggled() ? "Sprint: ON" : "Sprint: OFF"));
                }
            }
            while (worldMapKeyBind.consumeClick()) {
                Module m = ModuleManager.getInstance().getModule("XaeroWorldmap");
                if (m != null && m.isEnabled()) {
                    if (client.gui.screen() == null) {
                        client.setScreenAndShow(new com.thelads.core.client.gui.LadsWorldMapScreen(null));
                    }
                }
            }

            // Gameplay toggle modules — tick every client tick
            Module fb = ModuleManager.getInstance().getModule("Fullbright");
            if (fb instanceof FullbrightModule) {
                ((FullbrightModule) fb).tick(client);
            }
            Module ts = ModuleManager.getInstance().getModule("ToggleSprint");
            if (ts instanceof ToggleSprintModule) {
                ((ToggleSprintModule) ts).tick(client);
            }
            Module tsn = ModuleManager.getInstance().getModule("ToggleSneak");
            if (tsn instanceof ToggleSneakModule) {
                ((ToggleSneakModule) tsn).tick(client);
            }
            Module zm = ModuleManager.getInstance().getModule("Zoom");
            if (zm instanceof ZoomModule) {
                ((ZoomModule) zm).tick(client, zoomKeyBind.isDown());
            }
            Module pm = ModuleManager.getInstance().getModule("PerformanceManager");
            if (pm instanceof com.thelads.core.modules.PerformanceManagerModule) {
                ((com.thelads.core.modules.PerformanceManagerModule) pm).tick(client);
            }
            Module bf3 = ModuleManager.getInstance().getModule("BetterF3");
            if (bf3 instanceof com.thelads.core.modules.BetterF3Module) {
                ((com.thelads.core.modules.BetterF3Module) bf3).tick(client);
            }

            // Sample clicks for the CPS counter / keystrokes overlay
            CpsTracker.get().tick(client);

            // Auto-reconnect countdown on the disconnect screen
            AutoReconnect.get().tick(client);

            // Auto-apply a HUD profile bound to the current server/world
            ProfileManager.get().onContext(ProfileContext.current(client));

            // Re-apply launcher profile if the file changed (account switch in launcher)
            LadsProfileSync.applyIfChanged(client);
        });
    }

    private static void setupOptimizations() {
        // ModernFix
        try {
            java.io.File modernFixConfig = new java.io.File("config/modernfix-mixins.properties");
            java.io.File parent = modernFixConfig.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            java.util.Properties props = new java.util.Properties();
            if (modernFixConfig.exists()) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(modernFixConfig)) {
                    props.load(in);
                }
            }
            props.setProperty("mixin.perf.compact_entity_models", "true");
            props.setProperty("mixin.perf.dynamic_dfu", "true");
            props.setProperty("mixin.perf.dynamic_resources", "true");
            props.setProperty("mixin.perf.lazy_search_tree_registry", "true");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(modernFixConfig)) {
                props.store(out, "ModernFix configurations written by The Lads Core");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to write ModernFix config", e);
        }

        // LanguageReload
        updateJsonConfig("config/languagereload.json", "multilingualItemSearch", false);

        // Entity Texture Features
        updateJsonConfig("config/entity_texture_features.json", "alwaysCheckVanillaEmissiveSuffix", false);

        // C2ME
        updateC2METoml();

        // Enforce options.txt mipmaps = 0
        updateOptionsTxt();
    }

    private static void updateOptionsTxt() {
        try {
            java.io.File file = new java.io.File("options.txt");
            java.util.List<String> lines = new java.util.ArrayList<>();
            boolean foundMipmap = false;
            if (file.exists()) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.trim().startsWith("mipmapLevels:")) {
                            lines.add("mipmapLevels:0");
                            foundMipmap = true;
                        } else {
                            lines.add(line);
                        }
                    }
                }
            }
            if (!foundMipmap) {
                lines.add("mipmapLevels:0");
            }
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file))) {
                for (String line : lines) {
                    pw.println(line);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to update options.txt", e);
        }
    }

    private static void updateJsonConfig(String filePath, String key, Object value) {
        try {
            java.io.File file = new java.io.File(filePath);
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            if (file.exists()) {
                try (java.io.FileReader reader = new java.io.FileReader(file)) {
                    com.google.gson.JsonObject parsed = new com.google.gson.Gson().fromJson(reader, com.google.gson.JsonObject.class);
                    if (parsed != null) {
                        json = parsed;
                    }
                } catch (Exception e) {
                    // Ignore, start empty
                }
            }
            if (value instanceof Boolean) {
                json.addProperty(key, (Boolean) value);
            } else if (value instanceof Number) {
                json.addProperty(key, (Number) value);
            } else if (value instanceof String) {
                json.addProperty(key, (String) value);
            }
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to update config file: " + filePath, e);
        }
    }

    private static void updateC2METoml() {
        try {
            java.io.File file = new java.io.File("config/c2me.toml");
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            java.util.List<String> lines = new java.util.ArrayList<>();
            if (file.exists()) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        lines.add(line);
                    }
                }
            }

            // Update or add gcFreeChunkSerializer = true (before any sections)
            boolean foundGcFree = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("[")) {
                    break;
                }
                if (line.startsWith("gcFreeChunkSerializer")) {
                    lines.set(i, "gcFreeChunkSerializer = true");
                    foundGcFree = true;
                    break;
                }
            }
            if (!foundGcFree) {
                lines.add(0, "gcFreeChunkSerializer = true");
            }

            // Find [noTickViewDistance] section
            int sectionIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.equals("[noTickViewDistance]")) {
                    sectionIndex = i;
                    break;
                }
            }

            if (sectionIndex == -1) {
                lines.add("");
                lines.add("[noTickViewDistance]");
                lines.add("chunkSendingSpeedMultiplierPercentage = 0");
                lines.add("maxConcurrentChunkLoads = 128");
            } else {
                boolean foundMultiplier = false;
                boolean foundMaxLoads = false;
                int nextSectionIndex = lines.size();
                for (int i = sectionIndex + 1; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.startsWith("[")) {
                        nextSectionIndex = i;
                        break;
                    }
                    if (line.startsWith("chunkSendingSpeedMultiplierPercentage")) {
                        lines.set(i, "chunkSendingSpeedMultiplierPercentage = 0");
                        foundMultiplier = true;
                    }
                    if (line.startsWith("maxConcurrentChunkLoads")) {
                        lines.set(i, "maxConcurrentChunkLoads = 128");
                        foundMaxLoads = true;
                    }
                }
                if (!foundMaxLoads) {
                    lines.add(nextSectionIndex, "maxConcurrentChunkLoads = 128");
                    if (nextSectionIndex < lines.size()) nextSectionIndex++;
                }
                if (!foundMultiplier) {
                    lines.add(nextSectionIndex, "chunkSendingSpeedMultiplierPercentage = 0");
                }
            }

            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file))) {
                for (String line : lines) {
                    pw.println(line);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to update c2me.toml", e);
        }
    }
}
