package com.thelads.core.v1_20_1.gui;

import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;

public class LadsSettingsScreen1201 extends Screen {
    private final Screen parent;
    private final LadsSettingsScreen ui;

    public LadsSettingsScreen1201(Screen parent) {
        super(Component.literal("Lads Settings"));
        this.parent = parent;
        this.ui = new LadsSettingsScreen();
        this.ui.setOnOpenHudEditor(() -> {
            Minecraft.getInstance().setScreen(new DraggableHudScreen1201(this));
        });
        this.ui.setOnClose(this::onClose);
        this.ui.setOnNarrate(text -> {
            if (text != null && !text.isBlank()) {
                minecraft.getNarrator().sayNow(text);
            }
        });
        this.ui.setOnOpenResourcePacks(this::openResourcePacks);
        this.ui.setClipboardReader(() -> Minecraft.getInstance().keyboardHandler.getClipboard());
        this.ui.setOnOpenModSettings(modId -> ExternalModSettings1201.open(modId, this));
    }

    public void openModule(String name) {
        ui.openModule(name);
    }

    public LadsSettingsScreen ui() {
        return ui;
    }

    @Override
    protected void init() {
        super.init();
        ui.refreshCatalog();
    }

    private void openResourcePacks() {
        minecraft.setScreen(new PackSelectionScreen(minecraft.getResourcePackRepository(), packs -> {
            minecraft.options.updateResourcePacks(packs);
            minecraft.setScreen(this);
        }, minecraft.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // The Lads UI paints the full background
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        ui.setReducedMotion(minecraft.options.screenEffectScale().get() <= 0);
        ui.render(new GuiGraphicsLadsAdapter1201(g, this.font), mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ui.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (ui.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ui.keyPressed(keyCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (ui.charTyped(codePoint)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ui.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (ui.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    public void closeFromMenuKey() {
        ui.close();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
