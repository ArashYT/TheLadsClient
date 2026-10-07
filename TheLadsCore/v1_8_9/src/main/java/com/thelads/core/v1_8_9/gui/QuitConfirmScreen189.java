package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.gui.LadsPalette;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class QuitConfirmScreen189 extends GuiScreen {
    private final GuiScreen parent;

    public QuitConfirmScreen189(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cardWidth = 280;
        int cardHeight = 130;
        int cardX = (width - cardWidth) / 2;
        int cardY = (height - cardHeight) / 2;

        int btnWidth = 118;
        int btnHeight = 24;
        int btnY = cardY + cardHeight - 36;

        buttonList.add(new GuiButton(0, cardX + 16, btnY, btnWidth, btnHeight, "Cancel"));
        buttonList.add(new GuiButton(1, cardX + cardWidth - 16 - btnWidth, btnY, btnWidth, btnHeight, "Quit Game"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(parent);
        } else if (button.id == 1) {
            mc.shutdown();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0x99000000);

        int cardWidth = 280;
        int cardHeight = 130;
        int cardX = (width - cardWidth) / 2;
        int cardY = (height - cardHeight) / 2;

        drawRect(cardX, cardY, cardX + cardWidth, cardY + cardHeight, LadsPalette.CARD);
        drawRect(cardX + 4, cardY, cardX + cardWidth - 4, cardY + 2, LadsPalette.ACCENT);

        fontRendererObj.drawString("Quit Game", cardX + 18, cardY + 16, LadsPalette.TEXT);
        fontRendererObj.drawString("Are you sure you want to quit?", cardX + 18, cardY + 44, LadsPalette.TEXT);
        fontRendererObj.drawString("The Lads Client will close.", cardX + 18, cardY + 58, LadsPalette.MUTED);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
