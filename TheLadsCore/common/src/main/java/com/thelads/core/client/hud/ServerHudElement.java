package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.ladsapi.minigame.Caps;
import com.thelads.core.client.ladsapi.minigame.Fmt;
import com.thelads.core.client.ladsapi.minigame.HudState;
import com.thelads.core.client.ladsapi.minigame.HudState.Widget;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.HudModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server HUD widgets (Lads API, PROTOCOL.md section 16): one movable element per widget group, "Server HUD" for the first group
 * (main first, then by name) and "Server HUD 2" to "Server HUD 4" for the others. Cooldown (icon, sweep, charges), pips, meter,
 * text and timer rows. Version-neutral: everything is drawn through {@link LadsGraphics}. Also registers {@link RunHudElement}'s module.
 */
public class ServerHudElement extends HudElement {
    public static final String[] NAMES = {"Server HUD", "Server HUD 2", "Server HUD 3", "Server HUD 4"};
    static final int W = 118, PAD = 4, GAP = 3;

    private final int index;

    public ServerHudElement(int index) {
        this.index = index;
        this.moduleName = NAMES[index];
        this.width = W;
        this.height = 14;
    }

    /** Registers the modules of the server HUD elements and the run timer (before the config loads, so their options persist). */
    public static void registerModules() {
        for (String n : NAMES) hudModule(n, "Widgets a server shows through the Lads API: cooldowns, pips, meters, text, timers.");
        hudModule(RunHudElement.NAME, "A parkour or speedrun timer a server runs: time, checkpoints, split delta and the finish result.");
        ModuleSupport.registerBuiltIn(NAMES);
        ModuleSupport.registerBuiltIn(RunHudElement.NAME);
    }

    private static void hudModule(String name, String description) {
        ModuleManager mm = ModuleManager.getInstance();
        if (mm.getModule(name) != null) return;
        HudModule m = new HudModule(name, description);
        m.addOption(new SliderOption("Size", 100, 50, 200, 5));
        m.addOption(new ColorOption("Background", true, 0x80000000));
        m.addOption(new DropdownOption("Color mode", 0, "Static", "Chroma", "Chroma Fast", "Fade"));
        m.setCategory(Module.Category.HUD);
        m.setEnabled(true); // a server's widgets show by default; the player can switch them off
        mm.register(m);
    }

    /** The widget groups, main first and then by name. */
    static List<String> groups() {
        List<String> g = new ArrayList<>(Minigame.hud.groups());
        g.sort(Comparator.<String, Boolean>comparing(s -> !s.equals("main")).thenComparing(Comparator.naturalOrder()));
        return g;
    }

    private List<Widget> mine() {
        if (!Caps.declared(Caps.HUD)) return List.of();
        List<String> g = groups();
        return index < g.size() ? Minigame.hud.widgets(g.get(index)) : List.of();
    }

    private static Widget sample(HudState.Kind kind, long now) {
        return switch (kind) {
            case COOLDOWN -> new Widget("s1", kind, "main", 0, "Dash", 0x55FFFF, "minecraft:feather", 4000, 10000, 2, List.of(), 0, "", "",
                List.of(), 0, false, now);
            case PIPS -> new Widget("s2", kind, "main", 1, "Lives", 0xFF5555, null, 0, 1, -1,
                List.of(new HudState.Pip(0xFF5555, true), new HudState.Pip(0xFF5555, true), new HudState.Pip(0x555555, false)), 0, "", "",
                List.of(), 0, false, now);
            case METER -> new Widget("s3", kind, "main", 2, "", 0x55FF55, null, 0, 1, -1, List.of(), 0.65, "Charge", "65%", List.of(), 0,
                false, now);
            case TIMER -> new Widget("s4", kind, "main", 3, "Round", 0xFFAA00, null, 0, 1, -1, List.of(), 0, "", "", List.of(), 90_000, false,
                now);
            default -> new Widget("s5", kind, "main", 4, "", 0xFFFFFF, null, 0, 1, -1, List.of(), 0, "", "", List.of("Server text"), 0,
                false, now);
        };
    }

    private static List<Widget> samples(long now) {
        return List.of(sample(HudState.Kind.COOLDOWN, now), sample(HudState.Kind.PIPS, now), sample(HudState.Kind.METER, now),
            sample(HudState.Kind.TIMER, now), sample(HudState.Kind.TEXT, now));
    }

    static int rowHeight(Widget w) {
        return switch (w.kind()) {
            case COOLDOWN -> 18;
            case PIPS, TIMER -> 11;
            case METER -> 19;
            case TEXT -> Math.max(1, w.lines().size()) * 10;
        };
    }

    private int heightOf(List<Widget> rows) {
        if (rows.isEmpty()) return 1;
        int h = PAD * 2 - GAP;
        for (Widget w : rows) h += rowHeight(w) + GAP;
        return h;
    }

    private List<Widget> rows(boolean editor) {
        List<Widget> rows = mine();
        if (rows.isEmpty() && editor && index == 0) rows = samples(Minigame.now());
        return rows;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        List<Widget> rows = rows(editor);
        width = rows.isEmpty() ? 1 : W;
        height = heightOf(rows);
    }

    @Override
    public void render(LadsGraphics g) {
        draw(g, rows(false));
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        draw(g, rows(true));
    }

    private void draw(LadsGraphics g, List<Widget> rows) {
        if (rows.isEmpty()) return;
        long now = Minigame.now();
        int h = heightOf(rows);
        width = W;
        height = h;
        int bg = resolveBackground();
        if ((bg & 0xFF000000) != 0) g.fill(x, y, x + W, y + h, bg);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        int cy = y + PAD;
        for (Widget w : rows) {
            drawRow(g, w, x + PAD, cy, W - PAD * 2, now, shadow);
            cy += rowHeight(w) + GAP;
        }
    }

    static int rgb(int color) {
        return 0xFF000000 | (color & 0xFFFFFF);
    }

    private void drawRow(LadsGraphics g, Widget w, int rx, int ry, int rw, long now, boolean shadow) {
        int tint = rgb(w.color());
        switch (w.kind()) {
            case COOLDOWN -> {
                g.fill(rx, ry, rx + 18, ry + 18, 0x66000000);
                g.drawItemIcon(w.icon() == null ? "minecraft:compass" : w.icon(), rx + 1, ry + 1);
                double frac = HudState.cooldownFraction(w, now);
                long left = HudState.cooldownRemaining(w, now);
                if (frac > 0) { // the sweep: the part still to wait darkens the icon from the top
                    int cover = (int) Math.ceil(16 * frac);
                    g.fill(rx + 1, ry + 1, rx + 17, ry + 1 + cover, 0xAA000000);
                }
                if (w.charges() >= 0) g.drawText("x" + w.charges(), rx + 19 - g.textWidth("x" + w.charges()), ry + 10, 0xFFFFFFFF, true);
                int tx = rx + 22;
                String title = w.title().isEmpty() ? (left > 0 ? "Cooling down" : "Ready") : w.title();
                g.drawText(title, tx, ry + 1, 0xFFFFFFFF, shadow);
                String right = left > 0 ? String.format(java.util.Locale.ROOT, "%.1fs", left / 1000.0) : "";
                if (!right.isEmpty()) g.drawText(right, rx + rw - g.textWidth(right), ry + 1, 0xFFCCCCCC, shadow);
                int bw = rw - 22;
                g.fill(tx, ry + 12, tx + bw, ry + 16, 0x99000000);
                int fill = (int) Math.round(bw * (1 - frac));
                if (fill > 0) g.fill(tx, ry + 12, tx + fill, ry + 16, tint);
            }
            case PIPS -> {
                int tx = rx;
                if (!w.title().isEmpty()) {
                    g.drawText(w.title(), rx, ry + 1, 0xFFFFFFFF, shadow);
                    tx = rx + g.textWidth(w.title()) + 5;
                }
                for (int i = 0; i < w.pips().size(); i++) {
                    HudState.Pip p = w.pips().get(i);
                    int px = tx + i * 9;
                    g.fill(px, ry + 1, px + 7, ry + 9, 0x99000000);
                    if (p.filled()) g.fill(px + 1, ry + 2, px + 6, ry + 8, rgb(p.color()));
                    else g.fill(px + 1, ry + 2, px + 6, ry + 8, (rgb(p.color()) & 0x00FFFFFF) | 0x44000000);
                }
            }
            case METER -> {
                g.drawText(w.left(), rx, ry, 0xFFFFFFFF, shadow);
                if (!w.right().isEmpty()) g.drawText(w.right(), rx + rw - g.textWidth(w.right()), ry, 0xFFCCCCCC, shadow);
                g.fill(rx, ry + 11, rx + rw, ry + 18, 0x99000000);
                int fill = (int) Math.round((rw - 2) * w.value());
                if (fill > 0) g.fill(rx + 1, ry + 12, rx + 1 + fill, ry + 17, tint);
            }
            case TEXT -> {
                int ly = ry;
                for (String line : w.lines()) {
                    g.drawText(line, rx, ly + 1, tint, shadow);
                    ly += 10;
                }
            }
            case TIMER -> {
                if (!w.title().isEmpty()) g.drawText(w.title(), rx, ry + 1, 0xFFFFFFFF, shadow);
                String t = Fmt.clock(HudState.timerMs(w, now));
                g.drawText(t, rx + rw - g.textWidth(t), ry + 1, tint, shadow);
            }
        }
    }
}
