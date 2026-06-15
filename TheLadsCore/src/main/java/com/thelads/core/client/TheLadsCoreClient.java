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
    private static boolean earlyWindowChecked = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("The Lads Core Client initialized!");
        Capes.INSTANCE.onInitializeClient();

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
                Module nt = ModuleManager.getInstance().getModule("ToggleNametags");
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
