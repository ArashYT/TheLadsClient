package com.thelads.core.v1_20_1.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Optional bridge to the installed replay mod's browser (Replay Mod on 1.20.1; 26.3 uses Flashback). Recording and playback stay
 * owned by that mod; Lads only offers its replay viewer from the title and pause screens.
 */
public final class ReplayScreens1201 {
    private static final String MOD = "replaymod";

    private ReplayScreens1201() {}

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded(MOD);
    }

    public static void open(Screen parent) {
        if (!available()) return;
        PauseMultiplayer1201.leaveAndOpen(parent, "Replays", () -> {
            Minecraft mc = Minecraft.getInstance();
            try {
                Class<?> modClass = Class.forName("com.replaymod.replay.ReplayModReplay");
                Object mod = modClass.getField("instance").get(null);
                Class<?> viewer = Class.forName("com.replaymod.replay.gui.screen.GuiReplayViewer");
                Object screen = viewer.getConstructor(modClass).newInstance(mod);
                viewer.getMethod("display").invoke(screen);
            } catch (ReflectiveOperationException | LinkageError failure) {
                org.slf4j.LoggerFactory.getLogger("TheLadsCore").warn("Replay Mod's viewer could not open", failure);
                mc.setScreen(new AlertScreen(() -> mc.setScreen(new TitleScreen()),
                    Component.literal("Replays unavailable"), Component.literal("The installed Replay Mod could not open its viewer.")));
            }
        });
    }
}
