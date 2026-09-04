package com.thelads.core.v1_21_1.gui;

import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.v1_21_1.adapter.GuiGraphicsLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LadsSettingsScreen121 extends Screen {
    private final Screen parent;
    private final LadsSettingsScreen ui;

    public LadsSettingsScreen121(Screen parent) {
        super(Component.literal("Lads Settings"));
        this.parent = parent;
        this.ui = new LadsSettingsScreen();
        this.ui.setOnOpenHudEditor(() -> {
            Minecraft.getInstance().setScreen(new DraggableHudScreen121(this));
        });
        this.ui.setOnClose(this::onClose);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        ui.render(new GuiGraphicsLadsAdapter(guiGraphics, this.font), mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ui.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (ui.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
