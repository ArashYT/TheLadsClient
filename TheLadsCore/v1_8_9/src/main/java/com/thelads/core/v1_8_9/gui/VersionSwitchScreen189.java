package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.VersionSwitch;
import com.thelads.core.client.gui.LadsPalette;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class VersionSwitchScreen189 extends GuiScreen {
    private final GuiScreen parent;
    private String pendingVersion = null;

    public VersionSwitchScreen189(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cardWidth = 320;
        int cardHeight = 220;
        int cardX = (width - cardWidth) / 2;
        int cardY = (height - cardHeight) / 2;

        if (pendingVersion == null) {
            List<String> versions = VersionSwitch.available();
            String current = "1.8.9";
            int btnY = cardY + 60;
            int btnHeight = 24;
            int btnWidth = cardWidth - 32;

            int id = 10;
            if (versions.isEmpty()) {
                String[] defaults = new String[]{"26.3", "26.2", "1.8.9"};
                for (String v : defaults) {
                    if (!v.equals(current)) {
                        buttonList.add(new GuiButton(id++, cardX + 16, btnY, btnWidth, btnHeight, "Lads Client " + v));
                        btnY += btnHeight + 8;
                    }
                }
            } else {
                for (String v : versions) {
                    buttonList.add(new GuiButton(id++, cardX + 16, btnY, btnWidth, btnHeight, "Lads Client " + v));
                    btnY += btnHeight + 8;
                }
            }

            buttonList.add(new GuiButton(0, cardX + 16, cardY + cardHeight - 34, btnWidth, 22, "Cancel"));
        } else {
            int btnWidth = (cardWidth - 40) / 2;
            int btnY = cardY + cardHeight - 36;

            buttonList.add(new GuiButton(1, cardX + 16, btnY, btnWidth, 24, "Cancel"));
            buttonList.add(new GuiButton(2, cardX + cardWidth - 16 - btnWidth, btnY, btnWidth, 24, "Relaunch Now"));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(parent);
        } else if (button.id == 1) {
            pendingVersion = null;
            initGui();
        } else if (button.id == 2) {
            VersionSwitch.request(pendingVersion);
            mc.shutdown();
        } else if (button.id >= 10) {
            String label = button.displayString;
            pendingVersion = label.replace("Lads Client ", "").trim();
            initGui();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0xAA000000);

        int cardWidth = 320;
        int cardHeight = 220;
        int cardX = (width - cardWidth) / 2;
        int cardY = (height - cardHeight) / 2;

        drawRect(cardX, cardY, cardX + cardWidth, cardY + cardHeight, LadsPalette.CARD);
        drawRect(cardX + 4, cardY, cardX + cardWidth - 4, cardY + 2, LadsPalette.ACCENT);

        if (pendingVersion == null) {
            fontRendererObj.drawString("Switch Versions", cardX + 18, cardY + 16, LadsPalette.TEXT);
            fontRendererObj.drawString("Running version: 1.8.9", cardX + 18, cardY + 34, LadsPalette.MUTED);
        } else {
            fontRendererObj.drawString("Confirm Version Switch", cardX + 18, cardY + 16, LadsPalette.TEXT);
            fontRendererObj.drawString("Switch to Lads Client " + pendingVersion + "?", cardX + 18, cardY + 44, LadsPalette.TEXT);
            fontRendererObj.drawString("The game will close and relaunch in " + pendingVersion + ".", cardX + 18, cardY + 60, LadsPalette.MUTED);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
