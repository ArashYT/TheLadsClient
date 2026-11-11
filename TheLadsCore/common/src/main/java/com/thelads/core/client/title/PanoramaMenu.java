package com.thelads.core.client.title;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.title.TitleOverlayLayout.Dropdown;
import com.thelads.core.client.title.TitleScreenTheme.Rect;
import com.thelads.core.client.gui.MenuGraphics;
import java.util.List;

/**
 * The title screen's Panorama dropdown: every panorama of PanoramaPicker with its name and version, the current one marked.
 * Picking a row applies it like the setting does. Clips and scrolls when long; closes on a click outside or Escape; Up/Down/Home/End
 * and Enter work. Drawn through LadsGraphics in canvas pixels; sends nothing.
 */
final class PanoramaMenu {
    private static final int PANEL = 0xF2160D10, LINE = 0xFF422029, HOVER = 0xFF2A171D, TEXT = 0xFFFFF8F8, MUTED = 0xFFC6B2B8,
        ACCENT = 0xFFFF5C70, SHADOW = 0x66000000;
    static final int KEY_ESC = 256, KEY_ENTER = 257, KEY_KP_ENTER = 335, KEY_UP = 265, KEY_DOWN = 264, KEY_HOME = 268, KEY_END = 269,
        KEY_PAGE_UP = 266, KEY_PAGE_DOWN = 267;

    private boolean open;
    private Rect anchor = new Rect(0, 0, 0, 0);
    private List<PanoramaPicker.Entry> rows = List.of();
    private String current = PanoramaPicker.DEFAULT;
    private int selected;
    private double scroll, target;
    private Dropdown layout;
    private double lastMouseX = -1, lastMouseY = -1;

    boolean isOpen() { return open; }

    /** Opens under the button at anchor; false (nothing opens, the title shows its note) while the release list is still loading. */
    boolean open(Rect anchor) {
        if (!PanoramaPicker.ready()) {
            PanoramaPicker.notifyLoading();
            return false;
        }
        this.anchor = anchor;
        rows = PanoramaPicker.entries();
        current = PanoramaPicker.current();
        selected = 0;
        for (int i = 0; i < rows.size(); i++) if (PanoramaPicker.isCurrent(rows.get(i), current)) selected = i;
        scroll = target = 0;
        layout = null;
        lastMouseX = lastMouseY = -1;
        open = true;
        return true;
    }

    void close() { open = false; }

    /** Whether a click here is on the button that opened it (a second press on it closes the list). */
    private boolean onAnchor(double x, double y) { return TitleOverlayLayout.inside(anchor, x, y); }

    private void layout(int sw, int sh) {
        layout = TitleOverlayLayout.dropdown(sw, sh, anchor, rows.size());
        target = TitleOverlayLayout.clampScroll((int) target, layout.contentHeight(), layout.list().height());
    }

    private int[] heights() {
        int[] h = new int[rows.size()];
        java.util.Arrays.fill(h, TitleOverlayLayout.ROW);
        return h;
    }

    void render(LadsGraphics g, int sw, int sh, int mx, int my, float dt) {
        if (!open) return;
        layout(sw, sh);
        Rect p = layout.panel(), l = layout.list();
        if (mx != lastMouseX || my != lastMouseY) { // the pointer moved: it chooses the row, the keys take over again when it rests
            if (lastMouseX >= 0 && TitleOverlayLayout.inside(l, mx, my)) {
                int at = TitleOverlayLayout.rowAt(heights(), (int) Math.round(scroll), my - l.y());
                if (at >= 0) selected = at;
            }
            lastMouseX = mx;
            lastMouseY = my;
        }
        scroll += (target - scroll) * (1 - Math.exp(-Math.min(0.1f, dt) * 18));
        if (Math.abs(target - scroll) < 0.3) scroll = target;
        int shown = (int) Math.round(scroll);
        g.overlay(() -> {
            g.fill(p.x() + 2, p.y() + 3, p.x() + p.width() + 2, p.y() + p.height() + 3, SHADOW);
            g.fill(p.x(), p.y(), p.x() + p.width(), p.y() + p.height(), LINE);
            g.fill(p.x() + 1, p.y() + 1, p.x() + p.width() - 1, p.y() + p.height() - 1, PANEL);
            g.enableScissor(l.x(), l.y(), l.x() + l.width(), l.y() + l.height());
            int barRoom = layout.maxScroll() > 0 ? 6 : 0;
            for (int i = 0; i < rows.size(); i++) {
                int y = l.y() + i * TitleOverlayLayout.ROW - shown;
                if (y + TitleOverlayLayout.ROW <= l.y() || y >= l.y() + l.height()) continue;
                PanoramaPicker.Entry row = rows.get(i);
                boolean isCurrent = PanoramaPicker.isCurrent(row, current);
                if (i == selected) g.fill(l.x(), y, l.x() + l.width() - barRoom, y + TitleOverlayLayout.ROW - 1, HOVER);
                if (isCurrent) g.fill(l.x(), y + 3, l.x() + 2, y + TitleOverlayLayout.ROW - 4, ACCENT);
                int textY = y + (TitleOverlayLayout.ROW - 1 - g.fontHeight()) / 2 + 1;
                int right = l.x() + l.width() - 8 - barRoom;
                int versionWidth = g.textWidth(row.version());
                if (isCurrent) TitleScreenTheme.icon(g, "check", l.x() + 7, y + (TitleOverlayLayout.ROW - 1 - 9) / 2, 9, ACCENT);
                int nameX = l.x() + 20;
                g.drawText(MenuGraphics.fit(g, row.name(), right - versionWidth - 8 - nameX), nameX, textY, isCurrent || i == selected ? TEXT : MUTED, false);
                g.drawText(row.version(), right - versionWidth, textY, MUTED, false);
            }
            g.disableScissor();
            if (layout.maxScroll() > 0) { // a slim scrollbar on the right
                int track = l.height(), thumb = Math.max(10, track * track / layout.contentHeight());
                int thumbY = l.y() + (int) ((track - thumb) * (shown / (double) layout.maxScroll()));
                g.fill(l.x() + l.width() - 3, l.y(), l.x() + l.width() - 1, l.y() + track, 0x33FFFFFF);
                g.fill(l.x() + l.width() - 3, thumbY, l.x() + l.width() - 1, thumbY + thumb, ACCENT & 0xAAFFFFFF);
            }
        });
    }

    /** A press while open: a row picks it, the button that opened the list or anywhere else outside closes it. Always consumed. */
    boolean click(double x, double y, int button) {
        if (!open) return false;
        if (layout == null) { close(); return true; }
        if (button == 0 && !onAnchor(x, y) && TitleOverlayLayout.inside(layout.list(), x, y)) {
            int at = TitleOverlayLayout.rowAt(heights(), (int) Math.round(scroll), (int) y - layout.list().y());
            if (at >= 0) {
                pick(at);
                return true;
            }
        }
        if (TitleOverlayLayout.inside(layout.panel(), x, y) && !onAnchor(x, y)) return true; // the panel's padding: stays open
        close();
        return true;
    }

    private void pick(int index) {
        PanoramaPicker.pick(rows.get(index).choice());
        close();
    }

    boolean wheel(double amount) {
        if (!open) return false;
        if (layout != null) target = TitleOverlayLayout.clampScroll((int) Math.round(target - amount * 24), layout.contentHeight(), layout.list().height());
        return true;
    }

    /** GLFW key codes. Everything else is swallowed while the list is open. */
    boolean key(int key) {
        if (!open) return false;
        int max = rows.size() - 1;
        switch (key) {
            case KEY_ESC -> close();
            case KEY_UP -> selected = Math.max(0, selected - 1);
            case KEY_DOWN -> selected = Math.min(max, selected + 1);
            case KEY_PAGE_UP -> selected = Math.max(0, selected - 4);
            case KEY_PAGE_DOWN -> selected = Math.min(max, selected + 4);
            case KEY_HOME -> selected = 0;
            case KEY_END -> selected = max;
            case KEY_ENTER, KEY_KP_ENTER -> pick(selected);
            default -> { return true; }
        }
        if (open && layout != null) {
            target = TitleOverlayLayout.scrollToShow(heights(), selected, (int) target, layout.list().height());
            lastMouseX = lastMouseY = -1;
        }
        return true;
    }
}
