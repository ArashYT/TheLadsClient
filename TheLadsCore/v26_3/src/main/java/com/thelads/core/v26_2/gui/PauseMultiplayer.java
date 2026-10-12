package com.thelads.core.v26_2.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/**
 * The pause menu's Multiplayer: the server list opens over the world (Back returns to the pause menu), and only joining a server
 * from it leaves the world or server first, through Minecraft's save/disconnect path (ConnectLeavesWorldMixin). Replays still ask
 * before leaving (leaveAndOpen).
 */
public final class PauseMultiplayer {
    private PauseMultiplayer() {}
    public static void open(Screen pause) {
        Minecraft mc=Minecraft.getInstance();
        if (!mc.allowsMultiplayer()) {
            mc.setScreenAndShow(new net.minecraft.client.gui.screens.AlertScreen(() -> mc.setScreenAndShow(pause),
                Component.translatable("menu.multiplayer"), Component.literal("Multiplayer is unavailable for this account.")));
            return;
        }
        mc.setScreenAndShow(mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(pause) : new SafetyScreen(pause));
    }
    /**
     * ConnectScreen.startConnecting (every join: server list, direct connect, LAN) while a world is open, which only the pause menu's
     * server list allows: the world is left first as Save and Quit / Disconnect leave it (a singleplayer world is saved, FastQuit's
     * background save included), and the screens after a failed connection lead to the title screen's server list instead of the
     * pause menu of a world that is gone. Returns the connect screen's parent.
     */
    public static Screen leaveWorld(Screen parent) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return parent;
        boolean local=mc.isLocalServer();
        org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads pause multiplayer: leaving the {} before connecting",local?"singleplayer world":"server");
        mc.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
        if(local)mc.disconnectWithSavingScreen();
        else mc.disconnectWithProgressScreen();
        com.thelads.core.v26_2.feature.fastquit.FastQuit26.awaitSaves(false);
        return parent instanceof JoinMultiplayerScreen?new JoinMultiplayerScreen(new TitleScreen()):parent instanceof net.minecraft.client.gui.screens.PauseScreen?new TitleScreen():parent;
    }
    public static void leaveAndOpen(Screen pause,String destination,Runnable next) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null){next.run();return;}
        boolean local=mc.isLocalServer();
        mc.setScreenAndShow(new ConfirmScreen(confirmed->{
            if(!confirmed){mc.setScreenAndShow(pause);return;}
            // Preserve the vanilla unsent-report prompt as well as local-world saving.
            mc.getReportingContext().draftReportHandled(mc,pause,()->{
                mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
                next.run();
            },true);
        },Component.literal(local?"Save and open "+destination+"?":"Leave this server?"),
          Component.literal(local?"Your world will be saved before "+destination+" opens.":"Disconnect from this server and open "+destination+"?"),
          Component.literal(local?"Save & continue":"Disconnect & continue"),Component.literal("Stay in game")));
    }
}
