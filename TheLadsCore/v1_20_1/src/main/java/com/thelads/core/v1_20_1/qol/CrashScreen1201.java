package com.thelads.core.v1_20_1.qol;

import com.thelads.core.client.CrashCatcher;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** Crash Catcher's screen after a caught crash: what crashed, Copy (the whole report) and Return to title. */
final class CrashScreen1201 extends Screen {
    private MultiLineLabel text = MultiLineLabel.EMPTY;

    CrashScreen1201() {
        super(Component.literal("The Lads Client caught a crash"));
    }

    @Override
    protected void init() {
        text = MultiLineLabel.create(font, Component.literal(CrashCatcher.summary + "\n\nYou left the world so the game could keep running. "
            + "The crash report is saved in the crash-reports folder."), width - 50);
        int y = Math.min(height - 50, 70 + text.getLineCount() * 9 + 12);
        addRenderableWidget(Button.builder(Component.literal("Copy crash report"), button -> minecraft.keyboardHandler.setClipboard(CrashCatcher.report))
            .bounds(width / 2 - 100, y, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Return to title"), button -> minecraft.setScreen(new TitleScreen()))
            .bounds(width / 2 - 100, y + 24, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 40, 0xFF5555);
        text.renderCentered(graphics, width / 2, 70);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
