package com.thelads.core.v26_2.gui;

import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class QuitConfirmScreen26 extends Screen {
    private final Screen parent;

    public QuitConfirmScreen26(Screen parent) {
        super(Component.literal("Quit Game"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cardWidth = 280;
        int cardHeight = 130;
        int cardX = (width - cardWidth) / 2;
        int cardY = (height - cardHeight) / 2;

        int btnWidth = 118;
        int btnHeight = 24;
        int btnY = cardY + cardHeight - 36;

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
            .bounds(cardX + 16, btnY, btnWidth, btnHeight).build());

        addRenderableWidget(Button.builder(Component.literal("Quit Game"), b -> {
            if (minecraft != null) minecraft.stop();
        }).bounds(cardX + cardWidth - 16 - btnWidth, btnY, btnWidth, btnHeight).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x99000000);

        int cardWidth = 280;
        int cardHeight = 130;
        int cardX = (width - cardWidth) / 2;
        int cardY = (height - cardHeight) / 2;

        var adapter = new GuiGraphicsExtractorLadsAdapter(g, font);
        g.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, LadsPalette.CARD);
        g.fill(cardX + 4, cardY, cardX + cardWidth - 4, cardY + 2, LadsPalette.ACCENT);

        g.text(font, "Quit Game", cardX + 18, cardY + 16, LadsPalette.TEXT, false);
        g.text(font, "Are you sure you want to quit?", cardX + 18, cardY + 44, LadsPalette.TEXT, false);
        g.text(font, "The Lads Client will close.", cardX + 18, cardY + 58, LadsPalette.MUTED, false);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
