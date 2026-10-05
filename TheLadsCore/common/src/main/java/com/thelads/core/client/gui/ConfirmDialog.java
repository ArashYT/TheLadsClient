package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import java.util.List;
import static com.thelads.core.client.gui.MenuGraphics.*;

/**
 * The small "Are you sure you want to reset?" every Lads reset asks first (HUD editor Reset, a module's Reset options, Flashback's
 * Reset). Modal: while it is open the screen under it gets no click, key or scroll. Confirm (or Enter) runs the reset; Cancel, Esc
 * or a click outside the box closes it and changes nothing.
 */
public final class ConfirmDialog {
    public static final String TITLE = "Are you sure you want to reset?";
    private String message = "";
    private Runnable onConfirm;
    private Rect box = new Rect(0, 0, 0, 0), confirm = box, cancel = box;

    /** Asks about this reset; {@code message} says what it resets. */
    public void open(String message, Runnable onConfirm) {
        this.message = message == null ? "" : message;
        this.onConfirm = java.util.Objects.requireNonNull(onConfirm);
    }
    public boolean isOpen() { return onConfirm != null; }
    public void close() { onConfirm = null; }
    /** Screen bounds of the buttons and the box as last drawn (QA clicks them with real events). */
    public Rect confirmBounds() { return confirm; }
    public Rect cancelBounds() { return cancel; }
    public Rect bounds() { return box; }

    public void render(LadsGraphics g, int mx, int my) {
        if (!isOpen()) return;
        int width = g.getScaledWidth(), height = g.getScaledHeight();
        int dw = Math.min(280, width - 24);
        List<String> lines = MenuGraphics.lines(g, message, dw - 24, 3);
        int dh = 30 + lines.size() * 12 + 10 + 22 + 8 + 12 + 10, dx = (width - dw) / 2, dy = Math.max(4, (height - dh) / 2);
        box = new Rect(dx, dy, dw, dh);
        int by = dy + 30 + lines.size() * 12 + 10, bw = (dw - 36) / 2;
        confirm = new Rect(dx + 12, by, bw, 22);
        cancel = new Rect(dx + dw - 12 - bw, by, bw, 22);

        g.fill(0, 0, width, height, 0xB0000000);
        round(g, dx - 2, dy + 3, dw + 4, dh + 4, 0x14000000);
        round(g, dx, dy, dw, dh, LadsPalette.BORDER);
        round(g, dx + 1, dy + 1, dw - 2, dh - 2, PANEL);
        g.fill(dx + 1, dy, dx + dw - 1, dy + 2, ACCENT);
        g.drawText(fit(g, TITLE, dw - 24), dx + 12, dy + 12, ACCENT);
        for (int i = 0; i < lines.size(); i++) g.drawText(lines.get(i), dx + 12, dy + 30 + i * 12, TEXT);

        boolean hoverConfirm = confirm.contains(mx, my), hoverCancel = cancel.contains(mx, my);
        round(g, confirm.x() - 1, confirm.y() - 1, confirm.width() + 2, confirm.height() + 2, ACCENT); // Enter's button
        round(g, confirm.x(), confirm.y(), confirm.width(), confirm.height(), hoverConfirm ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
        g.drawCenteredText("Confirm", confirm.x() + confirm.width() / 2, confirm.y() + (confirm.height() - g.fontHeight()) / 2 + 1, TEXT);
        round(g, cancel.x(), cancel.y(), cancel.width(), cancel.height(), hoverCancel ? LadsPalette.HOVER : CARD);
        g.drawCenteredText("Cancel", cancel.x() + cancel.width() / 2, cancel.y() + (cancel.height() - g.fontHeight()) / 2 + 1, TEXT);
        g.drawCenteredText(fit(g, "Enter confirms, Esc cancels", dw - 24), dx + dw / 2, by + 22 + 8, MUTED);
    }

    /** A press while open (always used: the dialog is modal). */
    public boolean click(double x, double y, int button) {
        if (!isOpen()) return false;
        if (button != 0) return true;
        if (confirm.contains(x, y)) accept();
        else if (cancel.contains(x, y) || !box.contains(x, y)) close();
        return true;
    }

    /** A key while open, GLFW codes: Enter (and keypad Enter) confirms, Esc cancels, every other key is swallowed. */
    public boolean key(int key) {
        if (!isOpen()) return false;
        if (key == 257 || key == 335) accept();
        else if (key == 256) close();
        return true;
    }

    private void accept() {
        Runnable run = onConfirm;
        close();
        run.run();
    }
}
