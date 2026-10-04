package com.thelads.core.modules;

import com.thelads.core.client.ChromaUtil;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Better F3: the game's own debug text, organised. Each version hands over one column of the debug screen as the game built it
 * (26.x DebugScreenOverlay.extractLines, 1.8.9 Forge's debug text event) and {@link #draw} replaces the plain grey text: lines are
 * sorted into sections by their vanilla labels (a line without one belongs to the section above it), hidden sections and the
 * engine counters players rarely need are left out, sections are kept apart by a gap, each label takes its section's colour and
 * each value white, and the lines slide in from the screen edge when the debug screen opens.
 */
public class BetterF3Module extends Module {
    public enum Section {
        PERFORMANCE("Show Performance", 0xFF7CF29C), POSITION("Show Position", 0xFF6FC3FF), WORLD("Show World", 0xFFFFD166),
        TARGET("Show Target", 0xFFFF8FA3), SYSTEM("Show System", 0xFFC59BFF);
        public final String option;
        final int color;
        Section(String option, int color) { this.option = option; this.color = color; }
    }

    /** One line to draw: a label ("XYZ: ") and its value, each with its colour; both empty for the gap between sections. */
    public record Line(String label, int labelColor, String value, int valueColor) {
        public boolean gap() { return label.isEmpty() && value.isEmpty(); }
    }

    static final int VALUE = 0xFFF2F2F2, PLAIN = 0xFFE0E0E0;
    private static final Line GAP = new Line("", 0, "", 0);
    private static final Pattern FPS = Pattern.compile("^(\\d+)(/\\d+)? fps");
    private static final Pattern BLOCK_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private static final Pattern FORMAT = Pattern.compile("§.");
    /** The vanilla labels of each section (1.8.9 and 26.x), matched at the start of a line. */
    private static final String[][] LABELS = {
        {"Minecraft ", "Integrated server @", "\"", "C: ", "E: ", "P: ", "Chunks[", "MultiplayerChunkCache", "ServerChunkCache", "Shader: ", "Post: ", "Filtering"},
        {"XYZ: ", "Block: ", "Chunk: ", "Facing: ", "Section-relative", "Chunk-relative"},
        {"Biome: ", "Server Biome", "Light: ", "Client Light", "Server Light", "Local Difficulty", "Day #", "CH ", "SH ", "SC: ", "Sounds", "Sound cache", "Blending"},
        {"Targeted Block", "Targeted Fluid", "Targeted Entity", "Looking at"},
        {"Java: ", "Mem: ", "Allocated: ", "Allocation rate", "Memory (", "CPU: ", "Display: ", "GPU: "}};
    /** Engine counters, duplicates of client values and key help: what Hide Inessential leaves out. */
    private static final String[] INESSENTIAL = {"C: ", "E: ", "P: ", "Chunks[", "MultiplayerChunkCache", "ServerChunkCache", "SC: ",
        "Sounds", "Sound cache", "Allocated: ", "Allocation rate", "Memory (", "Section-relative", "Chunk-relative", "CH ", "SH ",
        "Server Light", "Server Biome", "Local Difficulty", "Post: ", "Filtering", "Blending", "Debug charts", "To edit", "Debug: Pie",
        "For help"};
    private static final long REOPEN_GAP_MS = 500;
    private long lastDraw = Long.MIN_VALUE / 2, opened;
    private boolean wasVisible;

    public BetterF3Module() {
        super("BetterF3", "A cleaner F3 debug screen: colour-coded sections, inessential lines hidden, background, shadow and slide-in.");
        addOption(new BoolOption("Rainbow Colors", false));
        addOption(new BoolOption("Hide Inessential", true));
        addOption(new BoolOption("Text Shadow", true));
        addOption(new BoolOption("Background", true));
        addOption(new ColorOption("Background Color", false, 0x90505050));
        addOption(new BoolOption("Slide In", true));
        for (Section section : Section.values()) addOption(new BoolOption(section.option, true));
        setEnabled(true);
    }

    private boolean bool(String name) { return getOption(name) instanceof BoolOption option && option.get(); }

    /** The section a line's own label names, or null (a gap, key help, or a line that belongs to the one above). */
    static Section label(String plain) {
        if (FPS.matcher(plain).find()) return Section.PERFORMANCE;
        for (Section section : Section.values())
            for (String prefix : LABELS[section.ordinal()]) if (plain.startsWith(prefix)) return section;
        return BLOCK_ID.matcher(plain).matches() ? Section.TARGET : null;
    }

    static boolean inessential(String plain) {
        for (String prefix : INESSENTIAL) if (plain.startsWith(prefix)) return true;
        return false;
    }

    static int fpsColor(int fps) { return fps >= 60 ? 0xFF55FF55 : fps >= 30 ? 0xFFFFFF55 : 0xFFFF5555; }

    /** The lines of one column to draw, top down: sorted, filtered and coloured as the options say. */
    public List<Line> arrange(List<String> column) {
        boolean hide = bool("Hide Inessential"), rainbow = bool("Rainbow Colors");
        List<Line> out = new ArrayList<>();
        Section current = null, last = null;
        boolean blank = false, newSection = true, hiddenAbove = false;
        for (String text : column) {
            if (text == null || text.isEmpty()) { current = null; blank = newSection = true; hiddenAbove = false; continue; }
            String plain = FORMAT.matcher(text).replaceAll("");
            Section labelled = label(plain), section = labelled != null ? labelled : current;
            boolean header = newSection || section != current;
            current = section;
            newSection = false;
            // A line without a "label: " of its own continues the one above (26.x wraps its key help): hidden with it.
            boolean hidden = hide && (inessential(plain) || hiddenAbove && labelled == null && !plain.contains(": "));
            hiddenAbove = hidden;
            if (section != null && !bool(section.option) || hidden) continue;
            if (!out.isEmpty() && (blank || section != last)) out.add(GAP);
            blank = false;
            last = section;
            int color = rainbow ? ChromaUtil.chroma(out.size() * 150L, 4000L) : section == null ? PLAIN : section.color;
            Matcher fps = FPS.matcher(text);
            int split = text.indexOf(": ");
            if (fps.find()) out.add(new Line(fps.group(1) + " fps", fpsColor(Integer.parseInt(fps.group(1))), text.substring(fps.end()), VALUE));
            else if (split > 0 && split <= 24) out.add(new Line(text.substring(0, split + 2), color, text.substring(split + 2), VALUE));
            else out.add(new Line("", 0, text, header ? color : VALUE));
        }
        return out;
    }

    /** How far line {@code index} has slid in (0 off screen, 1 in place) {@code ms} after the debug screen opened. */
    static double slide(long ms, int index) {
        double t = Math.min(1, Math.max(0, (ms - index * 15) / 200.0));
        return 1 - Math.pow(1 - t, 3);
    }

    /**
     * Milliseconds since the debug screen opened. Every column of every frame asks: a frame drawn with the screen newly visible,
     * or after half a second without debug text (the game stops asking while it is closed), starts the slide again.
     */
    long sinceOpened(boolean visible, long now) {
        if (visible && !wasVisible || now - lastDraw > REOPEN_GAP_MS) opened = now;
        lastDraw = now;
        wasVisible = visible;
        return bool("Slide In") ? now - opened : Long.MAX_VALUE / 2;
    }

    /**
     * Draws one column of the debug screen as the game built it (top down; left or right aligned at {@code screenWidth}), as the
     * game's own column would be: from y 2, 9 pixels a line. {@code visible}: the debug screen itself is open (26.x also shows
     * single entries with it closed).
     */
    public void draw(LadsGraphics g, List<String> column, boolean left, int screenWidth, boolean visible) {
        long since = sinceOpened(visible, System.currentTimeMillis());
        boolean shadow = bool("Text Shadow");
        int background = !bool("Background") ? 0 : getOption("Background Color") instanceof ColorOption color
            ? color.isUseGlobal() ? HudSettings.getInstance().getGlobalBackground() : color.getColor() : 0x90505050;
        List<Line> lines = arrange(column);
        for (int i = 0, y = 2; i < lines.size(); i++, y += 9) {
            Line line = lines.get(i);
            if (line.gap()) continue;
            int labelWidth = g.textWidth(line.label()), width = labelWidth + g.textWidth(line.value());
            int offset = (int) Math.round((1 - slide(since, i)) * (width + 4));
            int x = left ? 2 - offset : screenWidth - 2 - width + offset;
            if (background >>> 24 != 0) g.fill(x - 1, y - 1, x + width + 1, y + 8, background);
            if (!line.label().isEmpty()) g.drawText(line.label(), x, y, line.labelColor(), shadow);
            g.drawText(line.value(), x + labelWidth, y, line.valueColor(), shadow);
        }
    }
}
