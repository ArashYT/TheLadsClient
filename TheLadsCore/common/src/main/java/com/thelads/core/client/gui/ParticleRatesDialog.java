package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.ParticleReductionOption;
import java.util.*;
import static com.thelads.core.client.gui.MenuGraphics.*;

/**
 * Particle Core's per-type editor: a searchable list of the game's particle ids, each with a 0 to 100% spawn slider (100 is
 * vanilla, 0 switches the type off). Opens over the settings screen like {@link ActionDropdown}; the screen saves on every change.
 */
final class ParticleRatesDialog {
    private static final int ROW = 25;
    private boolean open, changedOnly, dragging, confirmReset;
    private String query = "";
    private int offset, selected, x, y, w, h, dragRow = -1, trackX, trackW;
    private double scroll;
    private long frame, confirmUntil;
    private ParticleReductionOption option;
    private List<String> all = List.of(), filtered = List.of();
    private Runnable changed = () -> {};

    boolean isOpen() { return open; }

    void open(ParticleReductionOption value, Runnable save) {
        option = value; changed = save; all = value.ids().stream().sorted().toList();
        query = ""; changedOnly = dragging = confirmReset = false; selected = offset = 0; scroll = 0; frame = 0; dragRow = -1; open = true;
        filter();
    }

    private void filter() {
        String needle = query.toLowerCase(Locale.ROOT);
        filtered = all.stream().filter(id -> id.contains(needle) && (!changedOnly || option.percent(id) < 100)).toList();
        selected = Math.max(0, Math.min(selected, Math.max(0, filtered.size() - 1)));
        offset = 0; scroll = 0;
    }

    void render(LadsGraphics g, int mx, int my) {
        if (!open) return;
        w = Math.min(460, g.getScaledWidth() - 24); h = Math.min(400, g.getScaledHeight() - 24);
        x = (g.getScaledWidth() - w) / 2; y = (g.getScaledHeight() - h) / 2;
        long now = System.nanoTime();
        double dt = frame == 0 ? 0 : Math.min(.1, (now - frame) / 1e9);
        frame = now;
        if (confirmReset && now > confirmUntil) confirmReset = false;
        int listTop = y + 80, listBottom = y + h - 22;
        offset = Math.max(0, Math.min(Math.max(0, filtered.size() * ROW - (listBottom - listTop)), offset));
        scroll += (offset - scroll) * (1 - Math.exp(-dt * 20));
        g.fill(0, 0, g.getScaledWidth(), g.getScaledHeight(), 0xB8000000);
        round(g, x, y, w, h, PANEL);
        g.drawText("Particle spawn rates", x + 12, y + 11, TEXT);
        g.drawText("Done", x + w - 43, y + 11, ACCENT);
        int filterW = 74;
        g.fill(x + 10, y + 30, x + w - 10 - filterW - 6, y + 55, CARD);
        g.drawText(query.isEmpty() ? "Search particle types..." : query + "|", x + 17, y + 38, query.isEmpty() ? MUTED : TEXT);
        round(g, x + w - 10 - filterW, y + 30, filterW, 25, changedOnly ? LadsPalette.PRIMARY : CARD);
        g.drawCenteredText("Changed", x + w - 10 - filterW / 2, y + 38, TEXT);
        g.drawText("Drag a bar: 100% is vanilla, 0% never spawns. Right-click a row to reset it.", x + 12, y + 64, MUTED);
        int nameW = Math.max(70, Math.min(170, w * 2 / 5));
        trackX = x + 15 + nameW + 8; trackW = Math.max(40, x + w - 62 - trackX);
        g.enableScissor(x + 8, listTop, x + w - 8, listBottom);
        for (int i = 0; i < filtered.size(); i++) {
            String id = filtered.get(i);
            int ry = listTop + i * ROW - (int) Math.round(scroll);
            if (ry + ROW - 1 < listTop || ry > listBottom) continue;
            int percent = option.percent(id);
            boolean hover = mx >= x + 9 && mx < x + w - 9 && my >= Math.max(ry, listTop) && my < Math.min(ry + ROW - 1, listBottom);
            if (hover || i == selected) g.fill(x + 9, ry, x + w - 9, ry + ROW - 1, CARD);
            g.drawText(fit(g, id, nameW), x + 15, ry + 8, percent < 100 ? ACCENT : TEXT);
            g.fill(trackX, ry + 10, trackX + trackW, ry + 13, LadsPalette.BORDER);
            g.fill(trackX, ry + 10, trackX + Math.round(trackW * percent / 100f), ry + 13, ACCENT);
            String label = percent == 0 ? "Off" : percent + "%";
            g.drawText(label, x + w - 14 - g.textWidth(label), ry + 8, percent == 100 ? MUTED : TEXT);
        }
        g.disableScissor();
        if (filtered.isEmpty()) g.drawText(changedOnly ? "No changed particle types." : "No matching particle types.", x + 15, listTop + 8, MUTED);
        g.drawText(filtered.size() + " types, " + option.changed() + " changed", x + 12, y + h - 12, MUTED);
        String reset = confirmReset ? "Click again to reset all" : "Reset all";
        g.drawText(reset, x + w - 12 - g.textWidth(reset), y + h - 12, confirmReset ? ACCENT : MUTED);
    }

    private int index(double my) {
        int top = y + 80;
        return (int) Math.floor((my - top + Math.round(scroll)) / ROW);
    }

    private void apply(int row, double mx) {
        if (row < 0 || row >= filtered.size()) return;
        double t = Math.max(0, Math.min(1, (mx - trackX) / Math.max(1, trackW)));
        option.set(filtered.get(row), (int) Math.round(t * 20) * 5);
        selected = row;
        changed.run();
    }

    boolean click(double mx, double my, int button) {
        if (!open) return false;
        if (mx < x || mx >= x + w || my < y || my >= y + h || mx >= x + w - 54 && my < y + 28) { open = false; return true; }
        if (button == 0 && my >= y + 30 && my < y + 55 && mx >= x + w - 84) { changedOnly = !changedOnly; filter(); return true; }
        if (my >= y + h - 22) {
            if (mx >= x + w / 2.0) {
                if (confirmReset) { option.reset(); changed.run(); confirmReset = false; filter(); }
                else { confirmReset = true; confirmUntil = System.nanoTime() + 3_000_000_000L; }
            }
            return true;
        }
        if (my >= y + 80 && my < y + h - 22) {
            int row = index(my);
            if (row < 0 || row >= filtered.size()) return true;
            if (button == 1) { option.set(filtered.get(row), 100); selected = row; changed.run(); if (changedOnly) filter(); }
            else if (button == 0) {
                if (mx >= trackX - 4 && mx <= trackX + trackW + 4) { dragging = true; dragRow = row; apply(row, mx); } else selected = row;
            }
        }
        return true;
    }

    boolean drag(double mx) {
        if (!open || !dragging) return false;
        apply(dragRow, mx);
        return true;
    }

    boolean release() {
        if (!open || !dragging) return false;
        dragging = false; dragRow = -1;
        if (changedOnly) filter();
        return true;
    }

    boolean wheel(double amount) { if (!open) return false; offset -= (int) (amount * 30); return true; }

    boolean key(int key, int modifiers) {
        if (!open) return false;
        if (key == 256) { open = false; return true; }
        if (key == 259 && !query.isEmpty()) { query = query.substring(0, query.offsetByCodePoints(query.length(), -1)); filter(); }
        if (key == 65 && (modifiers & 2) != 0) { query = ""; filter(); }
        if ((key == 264 || key == 265) && !filtered.isEmpty()) {
            selected = Math.max(0, Math.min(filtered.size() - 1, selected + (key == 264 ? 1 : -1)));
            offset = Math.max(selected * ROW - (h - 126), Math.min(selected * ROW, offset));
        }
        if ((key == 262 || key == 263) && !filtered.isEmpty()) {
            int step = (modifiers & 2) != 0 ? 1 : 5;
            String id = filtered.get(selected);
            option.set(id, option.percent(id) + (key == 262 ? step : -step));
            changed.run();
        }
        if (key == 261 && !filtered.isEmpty()) { option.set(filtered.get(selected), 100); changed.run(); if (changedOnly) filter(); }
        return true;
    }

    boolean type(int cp) {
        if (!open) return false;
        if (Character.isValidCodePoint(cp) && !Character.isISOControl(cp) && query.length() < 64) { query += new String(Character.toChars(cp)); filter(); }
        return true;
    }
}
