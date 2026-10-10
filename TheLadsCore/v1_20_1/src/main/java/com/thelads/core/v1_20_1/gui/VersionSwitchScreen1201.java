package com.thelads.core.v1_20_1.gui;

import com.thelads.core.client.VersionSwitch;
import com.thelads.core.client.gui.LadsPalette;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class VersionSwitchScreen1201 extends Screen {
    private static final String RUNNING = "1.20.1";
    private final Screen parent;
    private String pendingVersion = null;
    private String error = null;

    public VersionSwitchScreen1201(Screen parent) {
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
            // The launcher's versions without the running one (the supported ones when it wrote no list).
            List<String> versions = VersionSwitch.available(RUNNING);
            int btnY = cardY + 60;
            int btnHeight = 24;
            int btnWidth = cardWidth - 32;

            for (String v : versions) {
                final String ver = v;
                addRenderableWidget(Button.builder(Component.literal("Lads Client " + ver), b -> {
                    this.pendingVersion = ver;
                    rebuildWidgets();
                }).bounds(cardX + 16, btnY, btnWidth, btnHeight).build());
                btnY += btnHeight + 8;
            }

            addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cardX + 16, cardY + cardHeight - 34, btnWidth, 22).build());
        } else {
            // Confirmation view
            int btnWidth = (cardWidth - 40) / 2;
            int btnY = cardY + cardHeight - 36;

            addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
                this.pendingVersion = null;
                this.error = null;
                rebuildWidgets();
            }).bounds(cardX + 16, btnY, btnWidth, 24).build());

            addRenderableWidget(Button.builder(Component.literal("Relaunch Now"), b -> {
                // The launcher starts the version once the game has closed; without the request that would only close it.
                if (!VersionSwitch.request(pendingVersion)) {
                    this.error = "Could not save the request for the launcher.";
                    return;
                }
                if (minecraft != null) minecraft.stop();
            }).bounds(cardX + cardWidth - 16 - btnWidth, btnY, btnWidth, 24).build());
        }
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xAA000000);

        int cardWidth = 320;
        int cardHeight = 220;
        int cardX = (width - cardWidth) / 2;
        int cardY = (height - cardHeight) / 2;

        g.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, LadsPalette.CARD);
        g.fill(cardX + 4, cardY, cardX + cardWidth - 4, cardY + 2, LadsPalette.ACCENT);

        if (pendingVersion == null) {
            g.drawString(font, "Switch Versions", cardX + 18, cardY + 16, LadsPalette.TEXT, false);
            g.drawString(font, "Running version: " + VersionSwitch.current(RUNNING), cardX + 18, cardY + 34, LadsPalette.MUTED, false);
        } else {
            g.drawString(font, "Confirm Version Switch", cardX + 18, cardY + 16, LadsPalette.TEXT, false);
            g.drawString(font, "Switch to Lads Client " + pendingVersion + "?", cardX + 18, cardY + 44, LadsPalette.TEXT, false);
            g.drawString(font, "The game will close and relaunch in that version.", cardX + 18, cardY + 62, LadsPalette.MUTED, false);
            if (error != null) g.drawString(font, error, cardX + 18, cardY + 84, LadsPalette.ACCENT, false);
        }

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
