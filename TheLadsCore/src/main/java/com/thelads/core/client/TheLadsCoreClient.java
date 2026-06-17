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
    private static boolean earlyWindowChecked = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("The Lads Core Client initialized!");
        Capes.INSTANCE.onInitializeClient();

        // Initialize Better Stats Screen client
        com.thelads.core.features.alwayson.betterstatisticscreen.client.BetterStatsClient.init();

        // Initialize Client Sort always-on client feature
        new com.thelads.core.features.alwayson.clientsort.ClientSortClientFabric().onInitializeClient();

        // ImmediatelyFast — early config init (onRenderSystemInit called via its MixinMinecraft)
        com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast.earlyInit();

        // Advancements Reloaded — loads mod config
        new com.thelads.core.features.alwayson.advancementsreloaded.AdvancementsReloadedFabric().onInitializeClient();

        // Entity Culling — registers tick event for culling
        new com.thelads.core.features.alwayson.entityculling.EntityCullingMod().onInitializeClient();

        // Raise Sound Limit — loads sound config and injector
        new com.thelads.core.features.alwayson.raisesoundlimit.RSLSMod().onInitializeClient();

        // 3D Skin Layers — initializes skin layer rendering
        new com.thelads.core.features.alwayson.skinlayers.SkinLayersMod().onInitializeClient();

        // Raised — client-side hotbar lift
        new dev.yurisuika.raised.RaisedClient().onInitializeClient();

        // Passive Shield — client-side shield rendering
        new com.natamus.passiveshield.ModFabricClient().onInitializeClient();

        // Immersive Hotbar — client-side hotbar rendering
        new derp.immersivehotbar.ImmersiveHotbarClient().onInitializeClient();

        // ScreenFX — client-side screen effect rendering
        new com.laryisland.screenfx.ScreenFX().onInitializeClient();

        // Fancy Door Animations — client-side door animation
        new io.github.yxmna.fancydooranim.FancyDoorAnimClient().onInitializeClient();

        // Threads — client-side thread display
        new com.threads.Threads().onInitializeClient();

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

        // Ixeris — UI improvements
        new me.decce.ixeris.fabric.IxerisModFabric().onInitializeClient();

        // Resource Pack Options — client-side pack config
        new dev.jfronny.respackopts.platform.fabric.RespackoptsClientFabric().onInitializeClient();

        // Enhanced Tooltips — richer item tooltip rendering
        new dev.ultimatchamp.enhancedtooltips.loaders.EnhancedTooltipsFabric().onInitializeClient();

        // Extreme Sound Muffler — per-sound volume control
        new com.leobeliik.extremesoundmuffler.SoundMufflerFabric().onInitializeClient();

        // Just Enough Items — client-side recipe browser
        new mezz.jei.fabric.JustEnoughItemsClient().onInitializeClient();

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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Last-resort cleanup: if the preLaunch loading window still wasn't
            // adopted by the time the game ticks, don't let it linger.
            if (!earlyWindowChecked) {
                earlyWindowChecked = true;
                LadsEarlyWindow.abandonIfUnused();
            }

            while (settingsKeyBind.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new LadsSettingsScreen(null));
                }
            }
            while (hudKeyBind.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new DraggableHudScreen());
                }
            }
            while (authKeyBind.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new AccountSwitcherScreen(null));
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
}
