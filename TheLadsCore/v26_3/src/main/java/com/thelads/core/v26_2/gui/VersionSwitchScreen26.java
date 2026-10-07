package com.thelads.core.v26_2.gui;

import com.thelads.core.client.VersionSwitch;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class VersionSwitchScreen26 extends Screen {
    private final Screen parent;
    private String pendingVersion = null;

    public VersionSwitchScreen26(Screen parent) {
        super(Component.literal("Switch Versions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cardWidth = 320;
        int cardHeight = 220;
        int cardX = (width - cardWidth) / 2;
        int cardY = (height - cardHeight) / 2;

        if (pendingVersion == null) {
            List<String> versions = VersionSwitch.available();
            String current = VersionSwitch.current();
            int btnY = cardY + 60;
            int btnHeight = 24;
            int btnWidth = cardWidth - 32;

            if (versions.isEmpty()) {
                // If versions list is empty, provide defaults or explanation
                String[] defaults = new String[]{"26.3", "26.2", "1.8.9"};
                for (String v : defaults) {
                    if (!v.equals(current)) {
                        final String ver = v;
                        addRenderableWidget(Button.builder(Component.literal("Lads Client " + ver), b -> {
                            this.pendingVersion = ver;
                            rebuildWidgets();
                        }).bounds(cardX + 16, btnY, btnWidth, btnHeight).build());
                        btnY += btnHeight + 8;
                    }
                }
            } else {
                for (String v : versions) {
                    final String ver = v;
                    addRenderableWidget(Button.builder(Component.literal("Lads Client " + ver), b -> {
                        this.pendingVersion = ver;
                        rebuildWidgets();
                    }).bounds(cardX + 16, btnY, btnWidth, btnHeight).build());
                    btnY += btnHeight + 8;
                }
            }

            addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cardX + 16, cardY + cardHeight - 34, btnWidth, 22).build());
        } else {
            // Confirmation view
            int btnWidth = (cardWidth - 40) / 2;
            int btnY = cardY + cardHeight - 36;

            addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
                this.pendingVersion = null;
                rebuildWidgets();
            }).bounds(cardX + 16, btnY, btnWidth, 24).build());

            addRenderableWidget(Button.builder(Component.literal("Relaunch Now"), b -> {
                VersionSwitch.request(pendingVersion);
                if (minecraft != null) minecraft.stop();
            }).bounds(cardX + cardWidth - 16 - btnWidth, btnY, btnWidth, 24).build());
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xAA000000);

        int cardWidth = 320;
        int cardHeight = 220;
        int cardX = (width - cardWidth) / 2;
        int cardY = (height - cardHeight) / 2;

        g.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, LadsPalette.CARD);
        g.fill(cardX + 4, cardY, cardX + cardWidth - 4, cardY + 2, LadsPalette.ACCENT);

        if (pendingVersion == null) {
            g.text(font, "Switch Versions", cardX + 18, cardY + 16, LadsPalette.TEXT, false);
            String cur = VersionSwitch.current();
            String curText = "Running version: " + (cur.isEmpty() ? "26.2" : cur);
            g.text(font, curText, cardX + 18, cardY + 34, LadsPalette.MUTED, false);
        } else {
            g.text(font, "Confirm Version Switch", cardX + 18, cardY + 16, LadsPalette.TEXT, false);
            g.text(font, "Switch to Lads Client " + pendingVersion + "?", cardX + 18, cardY + 44, LadsPalette.TEXT, false);
            g.text(font, "The game will close and relaunch in that version.", cardX + 18, cardY + 62, LadsPalette.MUTED, false);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
