package com.thelads.core.v1_21_11.gui;

import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.v1_21_11.adapter.GuiGraphicsLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class LadsSettingsScreen12111 extends Screen {
    private final Screen parent;
    private final LadsSettingsScreen ui;

    public LadsSettingsScreen12111(Screen parent) {
        super(Component.literal("Lads Settings"));
        this.parent = parent;
        this.ui = new LadsSettingsScreen();
        this.ui.setOnOpenHudEditor(() -> {
            Minecraft.getInstance().setScreen(new DraggableHudScreen12111(this));
        });
        this.ui.setOnClose(this::onClose);
        this.ui.setOnNarrate(text -> {
            // saySystemNow checks the user's narrator mode and whether speech is available.
            if (text != null && !text.isBlank()) minecraft.getNarrator().saySystemNow(text);
        });
        this.ui.setOnOpenResourcePacks(this::openResourcePacks);
        this.ui.setClipboardReader(() -> Minecraft.getInstance().keyboardHandler.getClipboard());
        this.ui.setOnOpenVideoSettings(() ->
            minecraft.setScreen(new VideoSettingsScreen(this, minecraft, minecraft.options)));
        this.ui.setOnOpenModSettings(modId -> ExternalModSettings.open(modId, this));
    }

    public void openModule(String name) { ui.openModule(name); }
    /** The shared menu this screen draws and routes input to (QA reads its state and control bounds). */
    public LadsSettingsScreen ui() { return ui; }

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
        // The common UI paints the full background; avoid the separate vanilla blur/panorama pass.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        ui.setReducedMotion(minecraft.options.screenEffectScale().get() <= 0);
        ui.render(new GuiGraphicsLadsAdapter(g, this.font), mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
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
    public boolean keyPressed(KeyEvent event) {
        if (ui.keyPressed(event.key(), event.modifiers())) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (ui.charTyped(event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (ui.mouseReleased(event.x(), event.y(), event.button())) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (ui.mouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY)) return true;
        return super.mouseDragged(event, deltaX, deltaY);
    }

    /** Save through the common UI's existing close path; no separate menu state. */
    public void closeFromMenuKey() { ui.close(); }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
