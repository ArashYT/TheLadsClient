package com.thelads.core.v1_20_1.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Opens whatever "Video Settings" the installed mods provide (vanilla's, Sodium's or Embeddium's own screen) by pressing the real
 * button of a real Options screen, and returns to {@code back} (the Lads settings) when that screen is closed. The relay
 * never shows itself.
 */
public final class VideoSettingsRelay1201 extends OptionsScreen {
    private final Screen back;
    private boolean opened;

    private VideoSettingsRelay1201(Screen back, Minecraft minecraft) {
        super(back, minecraft.options);
        this.back = back;
    }

    public static void open(Screen back) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new VideoSettingsRelay1201(back, mc));
    }

    @Override
    protected void init() {
        if (opened) {
            // The video screen returned here: continue to the Lads settings.
            minecraft.setScreen(back);
            return;
        }
        opened = true;
        super.init();
        String video = Component.translatable("options.video").getString();
        for (var child : children()) {
            if (child instanceof Button button && button.getMessage().getString().equals(video)) {
                button.onPress();
                return;
            }
        }
        // No such button (a mod rearranged Options): vanilla's screen.
        minecraft.setScreen(new net.minecraft.client.gui.screens.VideoSettingsScreen(this, minecraft.options));
    }
}
