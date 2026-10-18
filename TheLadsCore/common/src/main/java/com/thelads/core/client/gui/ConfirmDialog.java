package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import java.util.List;
import static com.thelads.core.client.gui.MenuGraphics.*;

/**
 * The small "Are you sure you want to reset?" every Lads reset asks first (HUD editor Reset, a module's Reset options, Flashback's
 * Reset). Modal: while it is open the screen under it gets no click, key or scroll. Confirm (or Enter) runs the reset; Cancel, Esc
 * or a click outside the box closes it and changes nothing.
 * {@link #openDanger} is the big red "use at your own risk" warning (a server-rule bypass): the whole screen goes red, the confirm
 * button arms only after {@link #DANGER_ARM_MS} and only a click on it confirms (Enter and clicks outside do nothing).
 */
public final class ConfirmDialog {
    public static final String TITLE = "Are you sure you want to reset?";
    public static final long DANGER_ARM_MS = 3000;
    private static final int DANGER_RED = 0xFFE01B1B, DANGER_DARK = 0xFF3A0606, DANGER_TINT = 0xC8500000;
    private String title = TITLE, message = "", confirmLabel = "Confirm";
    private Runnable onConfirm;
    private boolean danger;
    private long armedAt;
    private Rect box = new Rect(0, 0, 0, 0), confirm = box, cancel = box;

    /** Asks about this reset; {@code message} says what it resets. */
    public void open(String message, Runnable onConfirm) {
        open(TITLE, message, onConfirm);
    }

    /** Asks {@code title} (a delete, say) the same way. */
    public void open(String title, String message, Runnable onConfirm) {
        this.title = title == null ? TITLE : title;
        this.message = message == null ? "" : message;
        this.onConfirm = java.util.Objects.requireNonNull(onConfirm);
        this.confirmLabel = "Confirm";
        danger = false;
    }

    /** The red at-your-own-risk warning: {@code confirmLabel} on the button that arms after a few seconds. */
    public void openDanger(String title, String message, String confirmLabel, Runnable onConfirm) {
        open(title, message, onConfirm);
        this.confirmLabel = confirmLabel;
        danger = true;
        armedAt = System.currentTimeMillis() + DANGER_ARM_MS;
    }
    public boolean isDanger() { return isOpen() && danger; }
    private boolean armed() { return !danger || System.currentTimeMillis() >= armedAt; }
    public boolean isOpen() { return onConfirm != null; }
    public void close() { onConfirm = null; }
    /** Screen bounds of the buttons and the box as last drawn (QA clicks them with real events). */
    public Rect confirmBounds() { return confirm; }
    public Rect cancelBounds() { return cancel; }
    public Rect bounds() { return box; }

    public void render(LadsGraphics g, int mx, int my) {
        if (!isOpen()) return;
        if (danger) { renderDanger(g, mx, my); return; }
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
        g.drawText(fit(g, title, dw - 24), dx + 12, dy + 12, ACCENT);
        for (int i = 0; i < lines.size(); i++) g.drawText(lines.get(i), dx + 12, dy + 30 + i * 12, TEXT);

        boolean hoverConfirm = confirm.contains(mx, my), hoverCancel = cancel.contains(mx, my);
        round(g, confirm.x() - 1, confirm.y() - 1, confirm.width() + 2, confirm.height() + 2, ACCENT); // Enter's button
        round(g, confirm.x(), confirm.y(), confirm.width(), confirm.height(), hoverConfirm ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
        g.drawCenteredText("Confirm", confirm.x() + confirm.width() / 2, confirm.y() + (confirm.height() - g.fontHeight()) / 2 + 1, TEXT);
        round(g, cancel.x(), cancel.y(), cancel.width(), cancel.height(), hoverCancel ? LadsPalette.HOVER : CARD);
        g.drawCenteredText("Cancel", cancel.x() + cancel.width() / 2, cancel.y() + (cancel.height() - g.fontHeight()) / 2 + 1, TEXT);
        g.drawCenteredText(fit(g, "Enter confirms, Esc cancels", dw - 24), dx + dw / 2, by + 22 + 8, MUTED);
    }

    private void renderDanger(LadsGraphics g, int mx, int my) {
        int width = g.getScaledWidth(), height = g.getScaledHeight();
        int dw = Math.min(340, width - 16);
        List<String> lines = MenuGraphics.lines(g, message, dw - 24, 8);
        int dh = 62 + lines.size() * 12 + 12 + 24 + 22, dx = (width - dw) / 2, dy = Math.max(4, (height - dh) / 2);
        box = new Rect(dx, dy, dw, dh);
        int by = dy + 62 + lines.size() * 12 + 12, bw = (dw - 36) / 2;
        cancel = new Rect(dx + 12, by, bw, 24); // the safe choice comes first
        confirm = new Rect(dx + dw - 12 - bw, by, bw, 24);

        g.fill(0, 0, width, height, DANGER_TINT);
        round(g, dx - 3, dy - 3, dw + 6, dh + 6, DANGER_RED);
        round(g, dx, dy, dw, dh, DANGER_DARK);
        g.fill(dx, dy, dx + dw, dy + 6, DANGER_RED);
        g.pushPose();
        g.scale(2, 2);
        g.drawCenteredText(fit(g, "! " + title + " !", (dw - 16) / 2), (dx + dw / 2) / 2, (dy + 14) / 2, 0xFFFF3B3B);
        g.popPose();
        for (int i = 0; i < lines.size(); i++) g.drawText(lines.get(i), dx + 12, dy + 62 + i * 12, TEXT);

        boolean armed = armed(), hoverConfirm = armed && confirm.contains(mx, my), hoverCancel = cancel.contains(mx, my);
        round(g, cancel.x() - 1, cancel.y() - 1, cancel.width() + 2, cancel.height() + 2, TEXT);
        round(g, cancel.x(), cancel.y(), cancel.width(), cancel.height(), hoverCancel ? LadsPalette.HOVER : CARD);
        g.drawCenteredText("Cancel (safe)", cancel.x() + cancel.width() / 2, cancel.y() + (cancel.height() - g.fontHeight()) / 2 + 1, TEXT);
        round(g, confirm.x(), confirm.y(), confirm.width(), confirm.height(), !armed ? LadsPalette.DISABLED : hoverConfirm ? 0xFFFF2A2A : DANGER_RED);
        String label = armed ? confirmLabel : confirmLabel + " (" + ((armedAt - System.currentTimeMillis()) / 1000 + 1) + ")";
        g.drawCenteredText(fit(g, label, confirm.width() - 6), confirm.x() + confirm.width() / 2, confirm.y() + (confirm.height() - g.fontHeight()) / 2 + 1, TEXT);
        g.drawCenteredText(fit(g, "Esc cancels", dw - 24), dx + dw / 2, by + 24 + 8, MUTED);
    }

    /** A press while open (always used: the dialog is modal). */
    public boolean click(double x, double y, int button) {
        if (!isOpen()) return false;
        if (button != 0) return true;
        if (confirm.contains(x, y)) { if (armed()) accept(); }
        else if (cancel.contains(x, y) || !danger && !box.contains(x, y)) close();
        return true;
    }

    /** A key while open, GLFW codes: Enter (and keypad Enter) confirms, Esc cancels, every other key is swallowed. */
    public boolean key(int key) {
        if (!isOpen()) return false;
        if ((key == 257 || key == 335) && !danger) accept();
        else if (key == 256) close();
        return true;
    }

    private void accept() {
        Runnable run = onConfirm;
        close();
        run.run();
    }
}
