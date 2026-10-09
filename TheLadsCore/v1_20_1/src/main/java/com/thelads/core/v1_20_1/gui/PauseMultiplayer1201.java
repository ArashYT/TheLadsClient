package com.thelads.core.v1_20_1.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.network.chat.Component;

/** Leave through Minecraft's saving/disconnect path only after the player confirms. */
public final class PauseMultiplayer1201 {
    private PauseMultiplayer1201() {}

    public static void open(Screen pause) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.allowsMultiplayer()) {
            mc.setScreen(new AlertScreen(() -> mc.setScreen(pause),
                Component.translatable("menu.multiplayer"), Component.literal("Multiplayer is unavailable for this account.")));
            return;
        }
        Runnable showServers = () -> {
            Screen title = new TitleScreen();
            mc.setScreen(mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(title) : new SafetyScreen(title));
        };
        leaveAndOpen(pause, "Multiplayer", showServers);
    }

    public static void leaveAndOpen(Screen pause, String destination, Runnable next) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            next.run();
            return;
        }
        boolean local = mc.isLocalServer();
        mc.setScreen(new ConfirmScreen(confirmed -> {
            if (!confirmed) {
                mc.setScreen(pause);
                return;
            }
            // Preserve the vanilla unsent-report prompt as well as local-world saving.
            mc.getReportingContext().draftReportHandled(mc, pause, () -> {
                mc.level.disconnect();
                if (local) mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
                else mc.clearLevel();
                next.run();
            }, true);
        }, Component.literal(local ? "Save and open " + destination + "?" : "Leave this server?"),
            Component.literal(local ? "Your world will be saved before " + destination + " opens."
                : "Disconnect from this server and open " + destination + "?"),
            Component.literal(local ? "Save & continue" : "Disconnect & continue"), Component.literal("Stay in game")));
    }
}
