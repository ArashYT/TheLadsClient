package com.thelads.core.v26_2.gui;

import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class LadsSettingsScreen26 extends Screen {
    private final Screen parent;
    private final LadsSettingsScreen ui;

    public LadsSettingsScreen26(Screen parent) {
        super(Component.literal("Lads Settings"));
        this.parent = parent;
        this.ui = new LadsSettingsScreen();
        this.ui.setOnOpenHudEditor(() -> {
            Minecraft.getInstance().setScreenAndShow(new DraggableHudScreen26(this));
        });
        this.ui.setOnClose(this::onClose);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        ui.render(new GuiGraphicsExtractorLadsAdapter(g, this.font), mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (ui.mouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, isDouble);
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
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
