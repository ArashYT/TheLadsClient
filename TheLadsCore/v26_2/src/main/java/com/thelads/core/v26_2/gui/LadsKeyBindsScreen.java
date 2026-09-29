package com.thelads.core.v26_2.gui;

import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

/** Stable native controls surface, including when an upstream mod replaces the vanilla screen. */
public final class LadsKeyBindsScreen extends KeyBindsScreen {
    public LadsKeyBindsScreen(Screen parent, Options options) { super(parent, options); }
}
