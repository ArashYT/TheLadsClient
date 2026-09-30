package com.thelads.core.v1_21_1.gui;

import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.v1_21_1.adapter.GuiGraphicsLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
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
        this.ui.setOnNarrate(text -> {
            // sayNow checks the user's narrator mode and whether speech is available.
            if (text != null && !text.isBlank()) minecraft.getNarrator().sayNow(text);
        });
        this.ui.setOnOpenResourcePacks(this::openResourcePacks);
        this.ui.setClipboardReader(() -> Minecraft.getInstance().keyboardHandler.getClipboard());
        this.ui.setOnOpenVideoSettings(() ->
            minecraft.setScreen(new VideoSettingsScreen(this, minecraft, minecraft.options)));
        this.ui.setOnOpenModSettings(modId -> ExternalModSettings.open(modId, this));
    }

    public void openModule(String name) { ui.openModule(name); }

    @Override
    protected void init() {
        super.init();
        ui.refreshCatalog();
    }

    private void openResourcePacks() {
        minecraft.setScreen(new PackSelectionScreen(minecraft.getResourcePackRepository(), packs -> {
            // Use vanilla's apply path: persist selection and reload changed resources.
            minecraft.options.updateResourcePacks(packs);
            minecraft.setScreen(this);
        }, minecraft.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // The common UI paints the full background. 1.21.1 Screen.render starts with this pass, which would
        // blur and cover the menu drawn below, so it stays empty.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        ui.setReducedMotion(minecraft.options.screenEffectScale().get() <= 0);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ui.keyPressed(keyCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (ui.charTyped(character)) return true;
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ui.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (ui.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    /** Save through the common UI's existing close path; no separate menu state. */
    public void closeFromMenuKey() { ui.close(); }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
