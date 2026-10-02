package com.thelads.core.v26_2.embedded.etf.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public abstract class UScreen {

    public static Screen currentScreen() { return Minecraft.getInstance().gui.screen(); }
    public static void setScreen(Screen screen) { Minecraft.getInstance().gui.setScreen(screen); }
}
