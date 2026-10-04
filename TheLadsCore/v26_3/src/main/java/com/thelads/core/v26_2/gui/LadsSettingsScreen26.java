package com.thelads.core.v26_2.gui;

import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.input.CharacterEvent;
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
        this.ui.setOnNarrate(text -> {
            // saySystemNow checks the user's narrator mode and whether speech is available.
            if (text != null && !text.isBlank()) minecraft.getNarrator().saySystemNow(text);
        });
        this.ui.setOnOpenResourcePacks(this::openResourcePacks);
        this.ui.setClipboardReader(() -> Minecraft.getInstance().keyboardHandler.getClipboard());
        this.ui.setOnOpenVideoSettings(() ->
            minecraft.gui.setScreen(new VideoSettingsScreen(this, minecraft, minecraft.options)));
        this.ui.setOnOpenModSettings(modId -> ExternalModSettings.open(modId, this));
    }

    public void openGlobalColors(){ui.openGlobalColors();}
    public void openDisplayActions(){ui.openDisplayActions();}
    public void openModule(String name) { ui.openModule(name); }
    public void searchKillBanners(String query) { ui.searchKillBanners(query); }
    /** Opens the Installed mods view (also used by the QA capture chain). */
    public void openMods() { ui.openMods(); }
    public boolean isModsViewOpen() { return ui.isModsViewOpen(); }

    @Override
    protected void init() {
        super.init();
        ui.refreshCatalog();
    }

    private void openResourcePacks() {
        minecraft.gui.setScreen(new PackSelectionScreen(minecraft.getResourcePackRepository(), packs -> {
            // Use vanilla's apply path: persist selection and reload changed resources.
            minecraft.options.updateResourcePacks(packs);
            minecraft.gui.setScreen(this);
        }, minecraft.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // The common UI paints the full background; avoid the separate vanilla blur/panorama pass.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        minecraft.textInputManager().onTextInputFocusChange(this, ui.isEditingText());
        ui.setReducedMotion(minecraft.options.screenEffectScale().get() <= 0);
        ui.render(new GuiGraphicsExtractorLadsAdapter(g, this.font), mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (ui.mouseClicked(event.x(), event.y(), CommonInput.button(event.button()))) {
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
        if (ui.keyPressed(CommonInput.key(event), CommonInput.modifiers(event.modifiers()))) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (ui.charTyped(event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (ui.mouseReleased(event.x(), event.y(), CommonInput.button(event.button()))) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (ui.mouseDragged(event.x(), event.y(), CommonInput.button(event.button()), deltaX, deltaY)) return true;
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override public void removed() { minecraft.textInputManager().stopTextInput(this); super.removed(); }

    /** Save through the common UI's existing close path; no separate menu state. */
    public void closeFromMenuKey() { ui.close(); }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
