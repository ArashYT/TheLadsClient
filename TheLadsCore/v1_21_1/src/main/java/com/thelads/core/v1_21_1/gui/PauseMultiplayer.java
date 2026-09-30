package com.thelads.core.v1_21_1.gui;

import com.thelads.core.v1_21_1.mixin.PauseScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.network.chat.Component;

/** Leave through Minecraft's saving/disconnect path only after the player confirms (the 26.x pause Multiplayer flow). */
public final class PauseMultiplayer {
    private PauseMultiplayer() {}
    public static void open(Screen pause) {
        Minecraft mc=Minecraft.getInstance();
        if (!mc.allowsMultiplayer()) {
            mc.setScreen(new AlertScreen(() -> mc.setScreen(pause),
                Component.translatable("menu.multiplayer"), Component.literal("Multiplayer is unavailable for this account.")));
            return;
        }
        // As the title screen's Multiplayer button: the safety screen until the player skips it.
        Runnable showServers=()->{
            Screen title=new TitleScreen();
            mc.setScreen(mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(title) : new SafetyScreen(title));
        };
        leaveAndOpen(pause,"Multiplayer",showServers);
    }
    /** With a level loaded, pause is the PauseScreen: its own disconnect is the 1.21.1 vanilla path. */
    public static void leaveAndOpen(Screen pause,String destination,Runnable next) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null){next.run();return;}
        boolean local=mc.isLocalServer();
        mc.setScreen(new ConfirmScreen(confirmed->{
            if(!confirmed){mc.setScreen(pause);return;}
            // As the vanilla button: unsent-report prompt, then PauseScreen.onDisconnect saves or disconnects.
            mc.getReportingContext().draftReportHandled(mc,pause,()->{
                ((PauseScreenAccessor)pause).ladsDisconnect();
                next.run();
            },true);
        },Component.literal(local?"Save and open "+destination+"?":"Leave this server?"),
          Component.literal(local?"Your world will be saved before "+destination+" opens.":"Disconnect from this server and open "+destination+"?"),
          Component.literal(local?"Save & continue":"Disconnect & continue"),Component.literal("Stay in game")));
    }
}
