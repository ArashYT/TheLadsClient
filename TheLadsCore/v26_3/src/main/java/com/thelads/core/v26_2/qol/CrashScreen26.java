package com.thelads.core.v26_2.qol;

import com.thelads.core.client.CrashCatcher;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Crash Catcher's screen after a caught crash: what crashed, Copy (the whole report) and Return to title. */
final class CrashScreen26 extends Screen {
    private List<FormattedCharSequence> text = List.of();

    CrashScreen26() {
        super(Component.literal("The Lads Client caught a crash"));
    }

    @Override
    protected void init() {
        text = font.split(Component.literal(CrashCatcher.summary + "\n\nYou left the world so the game could keep running. "
            + "The crash report is saved in the crash-reports folder."), width - 50);
        int y = Math.min(height - 50, 70 + text.size() * 9 + 12);
        addRenderableWidget(Button.builder(Component.literal("Copy crash report"), button -> minecraft.keyboardHandler.setClipboard(CrashCatcher.report))
            .bounds(width / 2 - 100, y, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Return to title"), button -> minecraft.gui.setScreen(new TitleScreen()))
            .bounds(width / 2 - 100, y + 24, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(font, title, width / 2, 40, 0xFFFF5555);
        for (int i = 0; i < text.size(); i++) graphics.centeredText(font, text.get(i), width / 2, 70 + i * 9, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
