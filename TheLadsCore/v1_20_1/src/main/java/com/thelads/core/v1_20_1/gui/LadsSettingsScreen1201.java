package com.thelads.core.v1_20_1.gui;

import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import com.thelads.core.v1_20_1.feature.HudEditorView1201;
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
        this.ui.setClipboardWriter(text -> Minecraft.getInstance().keyboardHandler.setClipboard(text));
        this.ui.setOnOpenVideoSettings(() -> VideoSettingsRelay1201.open(this));
        this.ui.setOnOpenModSettings(modId -> ExternalModSettings1201.open(modId, this));
    }

    public void openGlobalColors() {
        ui.openGlobalColors();
    }

    public void openDisplayActions() {
        ui.openDisplayActions();
    }

    public void openModule(String name) {
        ui.openModule(name);
    }

    public void searchKillBanners(String query) {
        ui.searchKillBanners(query);
    }

    /** Opens the Installed mods view (also used by the QA capture chain). */
    public void openMods() {
        ui.openMods();
    }

    public boolean isModsViewOpen() {
        return ui.isModsViewOpen();
    }

    /** The shared menu this screen draws and routes input to (QA reads its bounds and state). */
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
            // Vanilla's apply path: persist the selection and reload the changed resources.
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
        if (com.thelads.core.v1_20_1.feature.UiQa1201.poseProbe) { // QA only: see UiQa1201
            com.thelads.core.v1_20_1.feature.UiQa1201.logPose("screen", g);
            com.thelads.core.v1_20_1.feature.UiQa1201.poseProbe = false;
        }
        var adapter = new GuiGraphicsLadsAdapter1201(g, this.font) {
            // Fullbright's preview shows the live world (no HUD), so a gamma change is seen as it is made.
            @Override
            public boolean drawGameView(int x, int y, int width, int height) {
                return HudEditorView1201.draw(g, x, y, width, height, false);
            }
        };
        adapter.managed(() -> ui.render(adapter, mouseX, mouseY));
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

    @Override
    public void removed() {
        HudEditorView1201.release();
        super.removed();
    }

    /** Save through the common UI's existing close path; no separate menu state. */
    public void closeFromMenuKey() {
        ui.close();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
