package com.thelads.core.v1_21_11.embedded.etf.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public abstract class UScreen {

    public static Screen currentScreen() { return Minecraft.getInstance().screen; }
    public static void setScreen(Screen screen) { Minecraft.getInstance().setScreen(screen); }
}
