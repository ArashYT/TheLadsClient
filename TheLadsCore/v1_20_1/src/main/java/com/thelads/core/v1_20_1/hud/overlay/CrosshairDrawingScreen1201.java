package com.thelads.core.v1_20_1.hud.overlay;

import com.thelads.core.client.CrosshairBackground;
import com.thelads.core.client.CrosshairDesign;
import com.thelads.core.client.CrosshairDrawing;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.DropdownOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Crosshair Tweaks' drawing editor (26.3 CrosshairDrawingScreen): edits stay local until Save; Escape and Cancel discard them. */
public final class CrosshairDrawingScreen1201 extends Screen {
    private final Screen parent;
    private CrosshairDrawing draft = Crosshair1201.drawing();
    private final ArrayDeque<CrosshairDrawing> history = new ArrayDeque<>();
    private int gridX, gridY, cell, lastX, lastY;
    private boolean painting, paintValue;
    private String error = "";

    public CrosshairDrawingScreen1201(Screen parent) {
        super(Component.literal("Crosshair drawing"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout();
        int width = Math.max(30, (this.width - 56) / 3), bottom = this.height - 58;
        addRenderableWidget(Button.builder(Component.literal("Undo"), b -> undo()).bounds(20, bottom, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> { checkpoint(); draft = new CrosshairDrawing(draft.width(), draft.height()); }).bounds(28 + width, bottom, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Size: " + draft.width()), b -> { resizeDrawing(); b.setMessage(Component.literal("Size: " + draft.width())); }).bounds(36 + width * 2, bottom, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Mirror"), b -> mirror()).bounds(20, bottom + 26, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(28 + width, bottom + 26, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save drawing"), b -> saveDrawing()).bounds(36 + width * 2, bottom + 26, width, 20).build());
    }

    private boolean hasPreview() { return CrosshairDesign.previewBox(width, height) != null; }

    private void layout() {
        var box = CrosshairDesign.previewBox(width, height);
        int availableWidth = box != null ? box.left() - 44 : this.width - 40;
        cell = Math.max(1, Math.min(availableWidth / draft.width(), Math.max(1, (height - 144) / draft.height())));
        gridX = (availableWidth - draft.width() * cell) / 2 + 20;
        gridY = 70 + Math.max(0, (height - 144 - draft.height() * cell) / 2);
    }

    private void checkpoint() {
        history.push(draft.copy());
        while (history.size() > 32) history.removeLast();
    }

    private void undo() {
        if (history.isEmpty()) return;
        draft = history.pop();
        layout();
    }

    private void mirror() {
        checkpoint();
        CrosshairDrawing next = new CrosshairDrawing(draft.width(), draft.height());
        for (int y = 0; y < draft.height(); y++) for (int x = 0; x < draft.width(); x++) next.set(draft.width() - 1 - x, y, draft.get(x, y));
        draft = next;
    }

    private void resizeDrawing() {
        checkpoint();
        int[] sizes = {9, 17, 25, 33, 49, 64};
        int index = 0;
        while (index < sizes.length && sizes[index] <= draft.width()) index++;
        draft = draft.resized(sizes[index % sizes.length], sizes[index % sizes.length]);
        layout();
    }

    private void saveDrawing() {
        try {
            Crosshair1201.commitDrawing(draft);
            ((DropdownOption) Crosshair1201.module().getOption("Shape")).setIndex(8);
            ConfigManager.save();
            onClose();
        } catch (java.io.IOException failure) {
            error = "Could not save drawing. Your edits are still here.";
            com.thelads.core.v1_20_1.hud.Hud1201.LOG.warn("Crosshair drawing save failed", failure);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        graphics.fill(0, 0, width, height, 0xf510131a);
        graphics.fill(0, 0, width, 2, 0xffa98aff);
        graphics.drawString(font, "CROSSHAIR / DRAWING", 20, 18, 0xffb99bff);
        graphics.drawString(font, "Left drag paints · Right drag erases · Ctrl+Z undoes", 20, 38, 0xffa9afbd);
        for (int y = 0; y < draft.height(); y++)
            for (int x = 0; x < draft.width(); x++) {
                int left = gridX + x * cell, top = gridY + y * cell;
                graphics.fill(left, top, left + cell, top + cell, ((x + y) & 1) == 0 ? 0xff202632 : 0xff29313e);
                if (draft.get(x, y)) graphics.fill(left, top, left + cell, top + cell, 0xffdccfff);
                if (cell >= 5) {
                    graphics.fill(left, top, left + 1, top + cell, 0x77000000);
                    graphics.fill(left, top, left + cell, top + 1, 0x77000000);
                }
            }
        var box = CrosshairDesign.previewBox(width, height);
        if (box != null) {
            graphics.drawString(font, "LIVE PREVIEW", box.left(), box.top() - 14, 0xffa9afbd);
            graphics.enableScissor(box.left(), box.top(), box.right(), box.bottom());
            // The background chosen on the settings page (a biome, gradient, colour, your image or the live game).
            CrosshairBackground.draw(new com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201(graphics, font) {
                @Override
                public boolean drawGameView(int x, int y, int w, int h) {
                    return com.thelads.core.v1_20_1.feature.HudEditorView1201.draw(graphics, x, y, w, h, false);
                }
            }, Crosshair1201.module(), box.left(), box.top(), box.right() - box.left(), box.bottom() - box.top());
            Crosshair1201.draw(graphics, (box.left() + box.right()) / 2, (box.top() + box.bottom()) / 2, Crosshair1201.number("Gap"), 1, false, draft);
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            graphics.disableScissor();
            if (box.bottom() + 30 <= height - 62) {
                graphics.drawString(font, "Uses your color,", box.left(), box.bottom() + 8, 0xffa9afbd);
                graphics.drawString(font, "scale and rotation.", box.left(), box.bottom() + 20, 0xffa9afbd);
            }
        }
        if (!error.isEmpty()) graphics.drawCenteredString(font, error, width / 2, height - 72, 0xffff7777);
        super.render(graphics, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) Math.floor((mouseX - gridX) / cell), y = (int) Math.floor((mouseY - gridY) / cell);
        if ((button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) && x >= 0 && y >= 0 && x < draft.width() && y < draft.height()) {
            checkpoint();
            painting = true;
            paintValue = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
            lastX = x;
            lastY = y;
            draft.set(x, y, paintValue);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (painting) {
            int x = Math.max(0, Math.min(draft.width() - 1, (int) Math.floor((mouseX - gridX) / cell)));
            int y = Math.max(0, Math.min(draft.height() - 1, (int) Math.floor((mouseY - gridY) / cell)));
            var line = new ArrayList<CrosshairDesign.Rect>();
            CrosshairDesign.line(line, lastX, lastY, x, y, 1);
            for (CrosshairDesign.Rect pixel : line) draft.set(pixel.left(), pixel.top(), paintValue);
            lastX = x;
            lastY = y;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (painting) { painting = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { undo(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void removed() {
        com.thelads.core.v1_20_1.feature.HudEditorView1201.release();
        super.removed();
    }
}
