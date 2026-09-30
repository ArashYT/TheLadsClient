package com.thelads.core.v26_2.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/** Leave through Minecraft's saving/disconnect path only after the player confirms. */
public final class PauseMultiplayer {
    private PauseMultiplayer() {}
    public static void open(Screen pause) {
        Minecraft mc=Minecraft.getInstance();
        if (!mc.allowsMultiplayer()) {
            mc.setScreenAndShow(new net.minecraft.client.gui.screens.AlertScreen(() -> mc.setScreenAndShow(pause),
                Component.translatable("menu.multiplayer"), Component.literal("Multiplayer is unavailable for this account.")));
            return;
        }
        Runnable showServers=()->{
            Screen title=new TitleScreen();
            mc.setScreenAndShow(mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(title) : new SafetyScreen(title));
        };
        leaveAndOpen(pause,"Multiplayer",showServers);
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
