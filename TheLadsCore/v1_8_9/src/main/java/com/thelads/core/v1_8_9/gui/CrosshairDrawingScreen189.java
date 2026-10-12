package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.CrosshairBackground;
import com.thelads.core.client.CrosshairDesign;
import com.thelads.core.client.CrosshairDrawing;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.feature.Crosshair189;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Crosshair Tweaks' drawing editor, as 26.x CrosshairDrawingScreen: edits stay local until Save; Escape and Cancel discard them. */
public final class CrosshairDrawingScreen189 extends GuiScreen {
    private static final int[] SIZES = {9, 17, 25, 33, 49, 64};
    private final GuiScreen parent;
    private final GameView189 view = new GameView189();
    private CrosshairDrawing draft = Crosshair189.drawing();
    private final ArrayDeque<CrosshairDrawing> history = new ArrayDeque<>();
    private int gridX, gridY, cell, lastX, lastY;
    private boolean painting, paintValue;
    private String error = "";

    public CrosshairDrawingScreen189(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        layout();
        int w = Math.max(30, (width - 56) / 3), bottom = height - 58;
        buttonList.clear();
        buttonList.add(new GuiButton(0, 20, bottom, w, 20, "Undo"));
        buttonList.add(new GuiButton(1, 28 + w, bottom, w, 20, "Clear"));
        buttonList.add(new GuiButton(2, 36 + w * 2, bottom, w, 20, "Size: " + draft.width()));
        buttonList.add(new GuiButton(3, 20, bottom + 26, w, 20, "Mirror"));
        buttonList.add(new GuiButton(4, 28 + w, bottom + 26, w, 20, "Cancel"));
        buttonList.add(new GuiButton(5, 36 + w * 2, bottom + 26, w, 20, "Save drawing"));
    }

    @Override
    public void onGuiClosed() { view.release(); }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0: undo(); break;
            case 1: checkpoint(); draft = new CrosshairDrawing(draft.width(), draft.height()); break;
            case 2:
                checkpoint();
                int index = 0;
                while (index < SIZES.length && SIZES[index] <= draft.width()) index++;
                draft = draft.resized(SIZES[index % SIZES.length], SIZES[index % SIZES.length]);
                layout();
                button.displayString = "Size: " + draft.width();
                break;
            case 3:
                checkpoint();
                CrosshairDrawing next = new CrosshairDrawing(draft.width(), draft.height());
                for (int y = 0; y < draft.height(); y++) for (int x = 0; x < draft.width(); x++) next.set(draft.width() - 1 - x, y, draft.get(x, y));
                draft = next;
                break;
            case 4: mc.displayGuiScreen(parent); break;
            case 5: save(); break;
            default: break;
        }
    }

    public boolean hasPreview() { return CrosshairDesign.previewBox(width, height) != null; }

    private void layout() {
        CrosshairDesign.Rect box = CrosshairDesign.previewBox(width, height);
        int available = box != null ? box.left() - 44 : width - 40;
        cell = Math.max(1, Math.min(available / draft.width(), Math.max(1, (height - 144) / draft.height())));
        gridX = (available - draft.width() * cell) / 2 + 20;
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

    private void save() {
        try {
            Crosshair189.commitDrawing(draft);
            ((DropdownOption) Crosshair189.module().getOption("Shape")).setIndex(8);
            ConfigManager.save();
            mc.displayGuiScreen(parent);
        } catch (IOException failure) {
            error = "Could not save drawing. Your edits are still here.";
            LogManager.getLogger("TheLadsCore").warn("Crosshair drawing save failed", failure);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Live Game: the world is copied before the screen paints over it.
        final boolean captured = Crosshair189.module().background.getIndex() == CrosshairBackground.LIVE && view.capture(mc);
        drawRect(0, 0, width, height, 0xf510131a);
        drawRect(0, 0, width, 2, 0xffa98aff);
        fontRendererObj.drawString("CROSSHAIR / DRAWING", 20, 18, 0xffb99bff);
        fontRendererObj.drawString("Left drag paints · Right drag erases · Ctrl+Z undoes", 20, 38, 0xffa9afbd);
        for (int y = 0; y < draft.height(); y++) for (int x = 0; x < draft.width(); x++) {
            int left = gridX + x * cell, top = gridY + y * cell;
            drawRect(left, top, left + cell, top + cell, ((x + y) & 1) == 0 ? 0xff202632 : 0xff29313e);
            if (draft.get(x, y)) drawRect(left, top, left + cell, top + cell, 0xffdccfff);
            if (cell >= 5) {
                drawRect(left, top, left + 1, top + cell, 0x77000000);
                drawRect(left, top, left + cell, top + 1, 0x77000000);
            }
        }
        CrosshairDesign.Rect box = CrosshairDesign.previewBox(width, height);
        if (box != null) {
            fontRendererObj.drawString("LIVE PREVIEW", box.left(), box.top() - 14, 0xffa9afbd);
            int scale = new ScaledResolution(mc).getScaleFactor();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(box.left() * scale, mc.displayHeight - box.bottom() * scale, (box.right() - box.left()) * scale, (box.bottom() - box.top()) * scale);
            // The background chosen on the settings page (a biome, gradient, colour, your image or the live game).
            CrosshairBackground.draw(new GuiLadsAdapter(fontRendererObj, width, height) {
                @Override public boolean drawGameView(int x, int y, int w, int h) { return captured && view.draw(x, y, w, h); }
            }, Crosshair189.module(), box.left(), box.top(), box.right() - box.left(), box.bottom() - box.top());
            Crosshair189.draw((box.left() + box.right()) / 2, (box.top() + box.bottom()) / 2, Crosshair189.number("Gap"), 1, false, draft);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (box.bottom() + 30 <= height - 62) {
                fontRendererObj.drawString("Uses your color,", box.left(), box.bottom() + 8, 0xffa9afbd);
                fontRendererObj.drawString("scale and rotation.", box.left(), box.bottom() + 20, 0xffa9afbd);
            }
        }
        if (!error.isEmpty()) drawCenteredString(fontRendererObj, error, width / 2, height - 72, 0xffff7777);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        int x = Math.floorDiv(mouseX - gridX, cell), y = Math.floorDiv(mouseY - gridY, cell);
        if ((button == 0 || button == 1) && x >= 0 && y >= 0 && x < draft.width() && y < draft.height()) {
            checkpoint();
            painting = true;
            paintValue = button == 0;
            lastX = x;
            lastY = y;
            draft.set(x, y, paintValue);
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long heldMillis) {
        if (!painting) return;
        int x = Math.max(0, Math.min(draft.width() - 1, Math.floorDiv(mouseX - gridX, cell)));
        int y = Math.max(0, Math.min(draft.height() - 1, Math.floorDiv(mouseY - gridY, cell)));
        List<CrosshairDesign.Rect> line = new ArrayList<>();
        CrosshairDesign.line(line, lastX, lastY, x, y, 1);
        for (CrosshairDesign.Rect pixel : line) draft.set(pixel.left(), pixel.top(), paintValue);
        lastX = x;
        lastY = y;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (painting) painting = false;
        else super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_Z && isCtrlKeyDown()) undo();
        else if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else super.keyTyped(typedChar, keyCode);
    }

    /** QA only: the draft being edited. */
    public CrosshairDrawing draft() { return draft.copy(); }
}
