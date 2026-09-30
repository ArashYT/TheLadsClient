package com.thelads.core.v1_21_11.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

/** Optional bridge to the official Flashback browser; recording/playback stay owned by Flashback. */
public final class FlashbackScreens {
    private FlashbackScreens() {}
    public static boolean available(){return FabricLoader.getInstance().isModLoaded("flashback");}
    public static void open(Screen parent) {
        if(!available())return;
        PauseMultiplayer.leaveAndOpen(parent,"Replays",()->{
            Minecraft mc=Minecraft.getInstance();
            try {
                Screen browser=(Screen)Class.forName("com.moulberry.flashback.screen.select_replay.SelectReplayScreen")
                    .getConstructor(Screen.class).newInstance(new TitleScreen());
                mc.setScreen(browser);
            }catch(ReflectiveOperationException|LinkageError failure){
                org.slf4j.LoggerFactory.getLogger("TheLadsCore").warn("Flashback browser could not open",failure);
                mc.setScreen(new AlertScreen(()->mc.setScreen(new TitleScreen()),
                    Component.literal("Replays unavailable"),Component.literal("The installed Flashback version could not open its browser.")));
            }
        });
    }
}
