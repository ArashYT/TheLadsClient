/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  net.fabricmc.api.ClientModInitializer
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
 *  net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.KeyMapping$Category
 *  net.minecraft.network.chat.Component
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.thelads.core.client;

import com.mojang.brigadier.CommandDispatcher;
import com.thelads.core.client.AutoReconnect;
import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.LadsEarlyWindow;
import com.thelads.core.client.LadsProfileSync;
import com.thelads.core.client.ModSyncNetworking;
import com.thelads.core.client.ProfileContext;
import com.thelads.core.client.auth.AccountSwitcherScreen;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.commands.KillBannerCommand;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ProfileManager;
import com.thelads.core.modules.FullbrightModule;
import com.thelads.core.modules.KillBannerModule;
import com.thelads.core.modules.PerformanceManagerModule;
import com.thelads.core.modules.ToggleSneakModule;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.modules.ZoomModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(value=EnvType.CLIENT)
public class TheLadsCoreClient
implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"modid");
    public static KeyMapping settingsKeyBind;
    public static KeyMapping hudKeyBind;
    public static KeyMapping authKeyBind;
    public static KeyMapping zoomKeyBind;
    public static KeyMapping nametagsKeyBind;
    private static boolean earlyWindowChecked;

    public void onInitializeClient() {
        LOGGER.info("The Lads Core Client initialized!");
        ConfigManager.load();
        ProfileManager.get().load();
        LadsProfileSync.read();
        for (HudElement el : HudManager.getInstance().getElements()) {
            int[] pos = HudSettings.getInstance().getPosition(el.getModuleName());
            if (pos == null) continue;
            el.setPosition(pos[0], pos[1]);
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            Module mod = ModuleManager.getInstance().getModule("KillBanner");
            if (mod instanceof KillBannerModule) {
                new KillBannerCommand((KillBannerModule)mod).register((CommandDispatcher<FabricClientCommandSource>)dispatcher);
            }
        });
        ModSyncNetworking.register();
        settingsKeyBind = KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping("key.thelads.settings", 344, KeyMapping.Category.MISC));
        hudKeyBind = KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping("key.thelads.hud", 345, KeyMapping.Category.MISC));
        authKeyBind = KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping("key.thelads.auth", 346, KeyMapping.Category.MISC));
        zoomKeyBind = KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping("key.thelads.zoom", 67, KeyMapping.Category.MISC));
        nametagsKeyBind = KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping("key.thelads.nametags", 72, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Module pm;
            Module zm;
            Module tsn;
            Module ts;
            if (!earlyWindowChecked) {
                earlyWindowChecked = true;
                LadsEarlyWindow.abandonIfUnused();
            }
            while (settingsKeyBind.consumeClick()) {
                if (client.screen != null) continue;
                client.setScreen((Screen)new LadsSettingsScreen(null));
            }
            while (hudKeyBind.consumeClick()) {
                if (client.screen != null) continue;
                client.setScreen((Screen)new DraggableHudScreen());
            }
            while (authKeyBind.consumeClick()) {
                if (client.screen != null) continue;
                client.setScreen((Screen)new AccountSwitcherScreen(null));
            }
            while (nametagsKeyBind.consumeClick()) {
                Module nt = ModuleManager.getInstance().getModule("ToggleNametags");
                if (nt == null) continue;
                nt.toggle();
                ConfigManager.save();
                if (client.player == null) continue;
                client.player.sendOverlayMessage((Component)Component.literal((String)(nt.isEnabled() ? "Nametags hidden" : "Nametags shown")));
            }
            Module fb = ModuleManager.getInstance().getModule("Fullbright");
            if (fb instanceof FullbrightModule) {
                ((FullbrightModule)fb).tick(client);
            }
            if ((ts = ModuleManager.getInstance().getModule("ToggleSprint")) instanceof ToggleSprintModule) {
                ((ToggleSprintModule)ts).tick(client);
            }
            if ((tsn = ModuleManager.getInstance().getModule("ToggleSneak")) instanceof ToggleSneakModule) {
                ((ToggleSneakModule)tsn).tick(client);
            }
            if ((zm = ModuleManager.getInstance().getModule("Zoom")) instanceof ZoomModule) {
                ((ZoomModule)zm).tick(client, zoomKeyBind.isDown());
            }
            if ((pm = ModuleManager.getInstance().getModule("PerformanceManager")) instanceof PerformanceManagerModule) {
                ((PerformanceManagerModule)pm).tick(client);
            }
            CpsTracker.get().tick(client);
            AutoReconnect.get().tick(client);
            ProfileManager.get().onContext(ProfileContext.current(client));
            LadsProfileSync.applyIfChanged(client);
        });
    }

    static {
        earlyWindowChecked = false;
    }
}

