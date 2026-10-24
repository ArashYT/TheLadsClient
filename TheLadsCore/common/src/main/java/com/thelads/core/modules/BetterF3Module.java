package com.thelads.core.modules;

import com.thelads.core.client.ChromaUtil;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Better F3: a cleaner debug screen. The default "BetterF3" style draws its own fixed list of lines from what the game knows
 * ({@link Info}, filled by each version): coloured "Key: value" lines, no plates, with shadow. The "Lads (old)" style keeps the game's
 * own debug text and organises it: lines sorted into sections by their vanilla labels, inessential engine counters hidden, a section
 * colour per label. Either way the columns slide in from the screen edges when the debug screen opens and out when it closes
 * ({@link Slide}, stepped frame by frame, so toggling F3 quickly reverses the slide from where it is).
 */
public class BetterF3Module extends Module {
    public enum Section {
        PERFORMANCE("Performance", 0xFF7CF29C, 0xFFFF7043, 0xFFFFFF55), POSITION("Position", 0xFF6FC3FF, 0xFFFF5555, 0xFFF2F2F2),
        WORLD("World", 0xFFFFD166, 0xFF4FE3B5, 0xFF55FF55), TARGET("Target", 0xFFFF8FA3, 0xFFFFAA00, 0xFFFFFF55),
        SYSTEM("System", 0xFFC59BFF, 0xFF55FFFF, 0xFFFFFF55);
        public final String option;
        /** The Lads (old) style's label colour. */
        final int color;
        final int key, value;
        Section(String title, int color, int key, int value) {
            this.option = "Show " + title;
            this.color = color;
            this.key = key;
            this.value = value;
        }
        String keyColor() { return option.substring(5) + " Key Color"; }
        String valueColor() { return option.substring(5) + " Value Color"; }
    }

    /** The BetterF3 style's lines, top down in each column; a new group starts after a gap. */
    public enum Row {
        FPS(true, 0, Section.PERFORMANCE, "FPS"), XYZ(true, 0, Section.POSITION, "XYZ"), BLOCK(true, 0, Section.POSITION, "Block"),
        PARTICLES(true, 0, Section.PERFORMANCE, "Particles"), ENTITIES(true, 0, Section.PERFORMANCE, "Entities rendered/total"),
        DIMENSION(true, 1, Section.WORLD, "Dimension"), FACING(true, 1, Section.WORLD, "Facing"), ROTATION(true, 1, Section.WORLD, "Rotation"),
        LIGHT(true, 1, Section.WORLD, "Light"), SERVER_LIGHT(true, 1, Section.WORLD, "Light (Server)"),
        HEIGHT(true, 1, Section.WORLD, "Highest Block"), SERVER_HEIGHT(true, 1, Section.WORLD, "Highest Block (Server)"),
        BIOME(true, 1, Section.WORLD, "Biome"), DIFFICULTY(true, 1, Section.WORLD, "Local Difficulty"),
        DAY_TICKS(true, 1, Section.WORLD, "Current Day Ticks"), DAYS(true, 1, Section.WORLD, "Days Played"), SLIME(true, 1, Section.WORLD, "Slime Chunk"),
        TARGET(false, 0, Section.TARGET, "Targeted Block"), TARGET_ID(false, 0, Section.TARGET, "Block ID"),
        TIME(false, 1, Section.SYSTEM, "Current Time"), JAVA(false, 1, Section.SYSTEM, "Java Version"), MEMORY(false, 1, Section.SYSTEM, "Memory Usage"),
        ALLOCATION(false, 1, Section.SYSTEM, "Allocation Rate"), ALLOCATED(false, 1, Section.SYSTEM, "Allocated Memory"),
        CPU(false, 1, Section.SYSTEM, "CPU"), DISPLAY(false, 1, Section.SYSTEM, "Display"), GPU(false, 1, Section.SYSTEM, "GPU"),
        GPU_UTIL(false, 1, Section.SYSTEM, "GPU Utilization"), GL(false, 1, Section.SYSTEM, "OpenGL Version"), DRIVER(false, 1, Section.SYSTEM, "GPU Driver");
        final boolean left;
        final int group;
        public final Section section;
        public final String key, option;
        Row(boolean left, int group, Section section, String key) {
            this.left = left;
            this.group = group;
            this.section = section;
            this.key = key;
            this.option = "Show " + key;
        }
    }

    /**
     * What the game knows this frame, filled by each version for the BetterF3 style. Whatever a version cannot know stays at its
     * default here and its line is left out (-1, NaN or null).
     */
    public static final class Info {
        public int fps = -1, fpsLimit;
        public double x = Double.NaN, y, z;
        public float yaw, pitch;
        /** The game's own counts: "61" particles, "E: 0/174, ..." entities (the numbers are taken from it). */
        public String particles, entities, dimension, biome;
        public int light = -1, skyLight, blockLight, serverSky = -1, serverBlock;
        /** Heightmaps as "S: 62  M: 62" (client) and "S: 105  O: 104  M: 104  ML: 104" (server). */
        public String heights, serverHeights;
        public double difficulty = -1, clampedDifficulty;
        /** The overworld's time of day in ticks. */
        public long dayTime = -1;
        /** The world seed: singleplayer only. */
        public Long seed;
        public int[] target;
        public String targetId, cpu, gpu, gpuVendor;
        /** The GL version string: "4.6.0 NVIDIA 610.88" (version, then driver). */
        public String glVersion;
        public int displayWidth, displayHeight;
        public double gpuUtilization = -1;
    }

    /** A piece of a line in one colour. */
    public record Seg(String text, int color) {}

    /** One line to draw: its pieces, and a key that stays the same while its value changes (the slide's order); no pieces = a gap. */
    public record Line(String key, List<Seg> segs) {
        public boolean gap() { return segs.isEmpty(); }
        public String text() {
            StringBuilder text = new StringBuilder();
            for (Seg seg : segs) text.append(seg.text());
            return text.toString();
        }
    }

    static final int VALUE = 0xFFF2F2F2, PLAIN = 0xFFE0E0E0;
    static final int[] AXIS = {0xFFFF5555, 0xFF55FF55, 0xFF55FFFF};
    public static final int BETTERF3 = 0, LADS = 1;
    private static final Line GAP = new Line("", List.of());
    private static final Pattern FPS = Pattern.compile("^(\\d+)(/\\d+)? fps");
    private static final Pattern BLOCK_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private static final Pattern DIMENSION_LINE = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+ FC: ");
    private static final Pattern PROPERTY = Pattern.compile("^[a-z0-9_]+: ");
    private static final Pattern ENTITY_COUNT = Pattern.compile("(\\d+)/(\\d+)");
    /** The game's particle count: its last number (26.x lists each kind, then "T" and the total). */
    private static final Pattern LAST_NUMBER = Pattern.compile("(\\d+)\\s*$");
    private static final Pattern SUB_KEY = Pattern.compile("[A-Za-z]+: ");
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
    /** The game's lines that the BetterF3 style's own lines already show (left out of its Other Lines). */
    private static final String[] SHOWN = {"XYZ: ", "Block: ", "Facing: ", "P: ", "E: ", "Light: ", "Client Light", "Server Light", "CH ",
        "SH ", "Biome: ", "Local Difficulty", "Day #", "Targeted Block", "Looking at", "Java: ", "Mem: ", "Allocated: ", "Allocation rate",
        "CPU: ", "Display: ", "GPU: "};
    /** Seconds a full slide takes at 100% speed; each line moves for 1 - SPREAD of it, the last one SPREAD after the first. */
    public static final double SLIDE_SECONDS = 0.35, SPREAD = 0.45;
    /** The longest step one frame takes (a hitch never eats the slide), and how long a slide may go undrawn before it counts as ended. */
    static final long MAX_STEP_NANOS = 50_000_000L, STALE_NANOS = 250_000_000L;

    private final Side leftSide = new Side(), rightSide = new Side();
    private final Set<Row> unsupported = EnumSet.noneOf(Row.class);

    public BetterF3Module() {
        super("BetterF3", "A cleaner F3 debug screen: coloured lines as the BetterF3 mod shows them (or the game's own text organised), "
            + "per-line toggles, shadow, background and a slide in and out.");
        addOption(new DropdownOption("Style", BETTERF3, "BetterF3", "Lads (old)"));
        addOption(new BoolOption("Text Shadow", true));
        addOption(new BoolOption("Background", false));
        addOption(new ColorOption("Background Color", false, 0x90505050));
        addOption(new BoolOption("Slide In", true));
        addOption(new SliderOption("Animation Speed", 100, 25, 400, 5).percent());
        addOption(new SliderOption("Update Interval", 100, 0, 1000, 50).suffix(" ms"));
        // 1.8.5: on by default, as requested: the Lads HUD modules step aside while the debug screen is open (whatever else Better F3 does).
        addOption(new BoolOption("Hide Lads HUD", true));
        addOption(new BoolOption("Rainbow Colors", false));
        addOption(new BoolOption("Show Other Lines", false));
        addOption(new BoolOption("Hide Inessential", true));
        addOption(new BoolOption("Axis Colors", true));
        for (Section section : Section.values()) {
            addOption(new BoolOption(section.option, true));
            addOption(new ColorOption(section.keyColor(), false, section.key));
            addOption(new ColorOption(section.valueColor(), false, section.value));
            for (Row row : Row.values()) if (row.section == section) addOption(new BoolOption(row.option, true));
        }
        setEnabled(true);
    }

    /**
     * Whether the whole Lads HUD steps aside while the debug screen is open (the "Hide Lads HUD" option, on by default, of Better F3
     * switched on). Otherwise only the widgets under the debug text do (HudCover).
     */
    public static boolean hidesLadsHud() {
        return com.thelads.core.config.ModuleManager.getInstance().getModule("BetterF3") instanceof BetterF3Module f3 && f3.isEnabled() && f3.bool("Hide Lads HUD");
    }

    /** Lines this version cannot know (1.8.9: server light, heightmaps, GPU utilization): their toggles are not shown. */
    public void unsupported(Row... rows) { unsupported.addAll(List.of(rows)); }

    /** Whether the BetterF3 style shows this line (if the game knows it). */
    public boolean shows(Row row) {
        return style() == BETTERF3 && !unsupported.contains(row) && bool(row.section.option) && bool(row.option);
    }

    /** Settings the screen leaves out: the other style's, a hidden section's lines, lines this version lacks. */
    public boolean hidden(Option option) {
        String name = option.getName();
        boolean lads = style() == LADS;
        if (name.equals("Background Color")) return !bool("Background");
        if (name.equals("Animation Speed")) return !bool("Slide In");
        if (name.equals("Hide Inessential")) return !lads && !bool("Show Other Lines");
        if (name.equals("Show Other Lines") || name.equals("Axis Colors")) return lads;
        for (Section section : Section.values())
            if (name.equals(section.keyColor()) || name.equals(section.valueColor())) return lads || !bool(section.option);
        for (Row row : Row.values()) if (name.equals(row.option)) return lads || unsupported.contains(row) || !bool(row.section.option);
        return false;
    }

    private boolean bool(String name) { return getOption(name) instanceof BoolOption option && option.get(); }
    private int color(String name, int fallback) { return getOption(name) instanceof ColorOption option ? option.getColor() : fallback; }
    public int style() { return getOption("Style") instanceof DropdownOption style ? style.getIndex() : BETTERF3; }

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

    /** Whether the BetterF3 style shows this game line itself. */
    static boolean shown(String plain) {
        if (FPS.matcher(plain).find() || BLOCK_ID.matcher(plain).matches() || DIMENSION_LINE.matcher(plain).find()) return true;
        for (String prefix : SHOWN) if (plain.startsWith(prefix)) return true;
        return false;
    }

    static int fpsColor(int fps) { return fps >= 60 ? 0xFF55FF55 : fps >= 30 ? 0xFFFFFF55 : 0xFFFF5555; }

    /** A load from 0 to 100%: green, through yellow, to red. */
    static int loadColor(double percent) {
        double t = Math.max(0, Math.min(1, percent / 100));
        int red = t < 0.5 ? (int) Math.round(0x55 + (0xFF - 0x55) * t * 2) : 0xFF, green = t < 0.5 ? 0xFF : (int) Math.round(0xFF - (0xFF - 0x55) * (t - 0.5) * 2);
        return 0xFF000000 | red << 16 | green << 8 | 0x55;
    }

    /** The Lads (old) style: the lines of one column to draw, top down: sorted, filtered and coloured as the options say. */
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
            if (fps.find()) out.add(line("fps", fps.group(1) + " fps", fpsColor(Integer.parseInt(fps.group(1))), text.substring(fps.end()), VALUE));
            else if (split > 0 && split <= 24) out.add(line(text.substring(0, split + 2), text.substring(0, split + 2), color, text.substring(split + 2), VALUE));
            else out.add(line(text, "", 0, text, header ? color : VALUE));
        }
        return out;
    }

    private static Line line(String key, String label, int labelColor, String value, int valueColor) {
        return new Line(key, label.isEmpty() ? List.of(new Seg(value, valueColor)) : List.of(new Seg(label, labelColor), new Seg(value, valueColor)));
    }

    /** The BetterF3 style: one column's lines from what the game knows, then (Show Other Lines) the game's lines it does not show. */
    public List<Line> betterF3(List<String> column, boolean left, Info info) {
        boolean rainbow = bool("Rainbow Colors");
        List<Line> out = new ArrayList<>();
        int group = -1;
        for (Row row : Row.values()) {
            if (row.left != left || !shows(row)) continue;
            int key = rainbow ? ChromaUtil.chroma(row.ordinal() * 150L, 4000L) : color(row.section.keyColor(), row.section.key);
            List<Seg> value = value(row, info, key, color(row.section.valueColor(), row.section.value));
            if (value == null) continue;
            if (!out.isEmpty() && row.group != group) out.add(GAP);
            group = row.group;
            List<Seg> segs = new ArrayList<>();
            if (row != Row.FPS) segs.add(new Seg(row.key + ": ", key));
            segs.addAll(value);
            out.add(new Line(row.name(), segs));
        }
        if (bool("Show Other Lines") && column != null) {
            List<Line> other = arrange(notShown(column));
            if (!other.isEmpty() && !out.isEmpty()) out.add(GAP);
            out.addAll(other);
        }
        return out;
    }

    /** The game's lines without the ones the BetterF3 style already shows (and the lines under them: a block's properties, the GL lines). */
    static List<String> notShown(List<String> column) {
        List<String> out = new ArrayList<>();
        boolean under = false;
        for (String text : column) {
            if (text == null || text.isEmpty()) { under = false; out.add(""); continue; }
            String plain = FORMAT.matcher(text).replaceAll("");
            boolean continues = label(plain) == null && (!plain.contains(": ") || PROPERTY.matcher(plain).find()) || plain.startsWith("#");
            if (shown(plain) || under && continues) { under = true; continue; }
            under = false;
            out.add(text);
        }
        return out;
    }

    /** A line's value pieces, or null when the game does not know it (the line is left out). */
    List<Seg> value(Row row, Info in, int key, int value) {
        boolean axis = bool("Axis Colors");
        return switch (row) {
            case FPS -> in.fps < 0 ? null : List.of(new Seg(in.fps + " fps / " + (in.fpsLimit <= 0 || in.fpsLimit >= 260 ? "Unlimited" : String.valueOf(in.fpsLimit)) + " fps", fpsColor(in.fps)));
            case XYZ -> Double.isNaN(in.x) ? null : axes(axis, value, format("%.3f", in.x), format("%.5f", in.y), format("%.3f", in.z));
            case BLOCK -> Double.isNaN(in.x) ? null : axes(axis, value, String.valueOf(floor(in.x)), String.valueOf(floor(in.y)), String.valueOf(floor(in.z)));
            case PARTICLES -> {
                Matcher total = in.particles == null ? null : LAST_NUMBER.matcher(in.particles);
                yield total == null || !total.find() ? null : List.of(new Seg(total.group(1), value));
            }
            case ENTITIES -> {
                Matcher count = in.entities == null ? null : ENTITY_COUNT.matcher(in.entities);
                yield count == null || !count.find() ? null : List.of(new Seg(count.group(1) + "/" + count.group(2), value));
            }
            case DIMENSION -> in.dimension == null ? null : List.of(new Seg(in.dimension, value));
            case FACING -> Double.isNaN(in.x) ? null : List.of(new Seg(facing(in.yaw), value));
            case ROTATION -> Double.isNaN(in.x) ? null : pairs(format("Yaw: %.1f  Pitch: %.1f", wrapDegrees(in.yaw), wrapDegrees(in.pitch)), key, value);
            case LIGHT -> in.light < 0 ? null : pairs("Total: " + in.light + "  Sky: " + in.skyLight + "  Block: " + in.blockLight, key, value);
            case SERVER_LIGHT -> in.serverSky < 0 ? null : pairs("Sky: " + in.serverSky + "  Block: " + in.serverBlock, key, value);
            case HEIGHT -> in.heights == null ? null : pairs(in.heights, key, value);
            case SERVER_HEIGHT -> in.serverHeights == null ? null : pairs(in.serverHeights, key, value);
            case BIOME -> in.biome == null ? null : List.of(new Seg(in.biome, value));
            case DIFFICULTY -> in.difficulty < 0 ? null : pairs(format("%.2f  Clamped: %.2f", in.difficulty, in.clampedDifficulty), key, value);
            case DAY_TICKS -> in.dayTime < 0 ? null : List.of(new Seg(String.valueOf(in.dayTime % 24000L), value));
            case DAYS -> in.dayTime < 0 ? null : List.of(new Seg(String.valueOf(in.dayTime / 24000L), value));
            case SLIME -> in.seed == null || Double.isNaN(in.x) ? null : List.of(new Seg(String.valueOf(slimeChunk(in.seed, floor(in.x) >> 4, floor(in.z) >> 4)), value));
            case TARGET -> in.target == null ? null : List.of(new Seg(in.target[0] + ", " + in.target[1] + ", " + in.target[2], value));
            case TARGET_ID -> in.target == null || in.targetId == null ? null : List.of(new Seg(in.targetId, value));
            case TIME -> List.of(new Seg(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")), value));
            case JAVA -> List.of(new Seg(System.getProperty("java.version") + " " + (System.getProperty("os.arch", "").contains("64")
                || "64".equals(System.getProperty("sun.arch.data.model")) ? 64 : 32) + "bit", value));
            case MEMORY -> {
                Runtime runtime = Runtime.getRuntime();
                long max = runtime.maxMemory(), used = runtime.totalMemory() - runtime.freeMemory();
                yield percent(used * 100.0 / max, " " + used / MB + "/" + max / MB + " MB", value);
            }
            case ALLOCATION -> List.of(new Seg(Allocation.rate() / MB + " MB/s", value));
            case ALLOCATED -> {
                Runtime runtime = Runtime.getRuntime();
                yield percent(runtime.totalMemory() * 100.0 / runtime.maxMemory(), " " + runtime.totalMemory() / MB + "MB", value);
            }
            case CPU -> in.cpu == null ? null : List.of(new Seg(in.cpu, value));
            case DISPLAY -> in.displayWidth <= 0 ? null : List.of(new Seg(in.displayWidth + " x " + in.displayHeight + (in.gpuVendor == null ? "" : " (" + in.gpuVendor + ")"), value));
            case GPU -> in.gpu == null ? null : List.of(new Seg(in.gpu, value));
            case GPU_UTIL -> in.gpuUtilization < 0 ? null : percent(Math.min(100, in.gpuUtilization), "", value);
            case GL -> in.glVersion == null ? null : List.of(new Seg(in.glVersion.split(" ", 2)[0], value));
            case DRIVER -> in.glVersion == null || !in.glVersion.trim().contains(" ") ? null : List.of(new Seg(in.glVersion.trim().split(" ", 2)[1], value));
        };
    }

    private static final long MB = 1024L * 1024L;
    private static String format(String format, Object... args) { return String.format(Locale.ROOT, format, args); }
    static int floor(double value) { return (int) Math.floor(value); }

    private static List<Seg> axes(boolean colored, int value, String x, String y, String z) {
        return List.of(new Seg(x + " ", colored ? AXIS[0] : value), new Seg(y + " ", colored ? AXIS[1] : value), new Seg(z, colored ? AXIS[2] : value));
    }

    private static List<Seg> percent(double percent, String rest, int value) {
        return rest.isEmpty() ? List.of(new Seg(Math.round(percent) + "%", loadColor(percent)))
            : List.of(new Seg(Math.round(percent) + "%", loadColor(percent)), new Seg(rest, value));
    }

    /** "Sky: 15  Block: 0": each "name: " in the key colour, the rest in the value colour. */
    static List<Seg> pairs(String text, int key, int value) {
        List<Seg> out = new ArrayList<>();
        Matcher name = SUB_KEY.matcher(text);
        int at = 0;
        while (name.find()) {
            if (name.start() > at) out.add(new Seg(text.substring(at, name.start()), value));
            out.add(new Seg(name.group(), key));
            at = name.end();
        }
        if (at < text.length()) out.add(new Seg(text.substring(at), value));
        return out;
    }

    /** The way a yaw faces, as the game names it: "West (Towards negative X)". */
    static String facing(float yaw) {
        return switch (floor(yaw / 90.0 + 0.5) & 3) {
            case 0 -> "South (Towards positive Z)";
            case 1 -> "West (Towards negative X)";
            case 2 -> "North (Towards negative Z)";
            default -> "East (Towards positive X)";
        };
    }

    /** A heightmap's short name as the game's debug screen prints it (Heightmap.Types names, 1.20.1 and 26.x). */
    public static String heightName(String type) {
        return switch (type) {
            case "WORLD_SURFACE_WG" -> "SW";
            case "WORLD_SURFACE" -> "S";
            case "OCEAN_FLOOR_WG" -> "OW";
            case "OCEAN_FLOOR" -> "O";
            case "MOTION_BLOCKING" -> "M";
            case "MOTION_BLOCKING_NO_LEAVES" -> "ML";
            default -> type;
        };
    }

    static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }

    /** Whether slimes spawn in this chunk of a world with this seed at any height below 40 (the game's chunk test). */
    static boolean slimeChunk(long seed, int x, int z) {
        return new java.util.Random(seed + (long) (x * x * 0x4c1906) + (long) (x * 0x5ac0db) + (long) (z * z) * 0x4307a7L + (long) (z * 0x5f24f) ^ 0x3ad8025fL).nextInt(10) == 0;
    }

    /** Heap allocated per second, from the heap in use every half second (a sample with a collection in it is skipped). */
    static final class Allocation {
        private static long lastTime, lastUsed, lastCollections = -1, rate;

        static synchronized long rate() {
            long now = System.currentTimeMillis();
            if (now - lastTime < 500) return rate;
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory(), collections = 0;
            for (var gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) collections += Math.max(0, gc.getCollectionCount());
            if (lastTime != 0 && collections == lastCollections) rate = Math.max(0, (used - lastUsed) * 1000 / (now - lastTime));
            lastTime = now;
            lastUsed = used;
            lastCollections = collections;
            return rate;
        }
    }

    /**
     * One column's slide: 0 off screen, 1 in place. Each frame it steps toward 1 while the debug screen is open and toward 0 after,
     * by that frame's time (at most {@link #MAX_STEP_NANOS}): toggling mid-way turns it around where it is, and one long frame never
     * skips it. A slide-out that went undrawn for a while (HUD hidden) has ended.
     */
    static final class Slide {
        double progress;
        private long last;
        private boolean stepped, open;

        double step(boolean open, long now, double seconds) {
            boolean stale = stepped && now - last > STALE_NANOS;
            if (stale && !this.open) progress = 0;
            double dt = !stepped || stale ? 0 : Math.max(0, Math.min(MAX_STEP_NANOS, now - last)) / 1e9;
            stepped = true;
            last = now;
            this.open = open;
            progress = seconds <= 0 ? open ? 1 : 0 : Math.max(0, Math.min(1, progress + (open ? dt : -dt) / seconds));
            return progress;
        }
    }

    /** How far a line in slot {@code slot} (0 the first in, 1 the last) is in, eased (0 off screen, 1 in place), at column progress p. */
    static double ease(double progress, double slot) {
        double t = Math.max(0, Math.min(1, (progress - slot * SPREAD) / (1 - SPREAD)));
        return 1 - Math.pow(1 - t, 3);
    }

    /** A column: its lines as last drawn, kept to slide out, and each line's slot by its key (first seen, first in). */
    static final class Side {
        final Slide slide = new Slide();
        List<Line> lines = List.of();
        final Map<String, Integer> order = new HashMap<>(), drawn = new java.util.LinkedHashMap<>();
        int screenWidth;
        long lastUpdateNanos;

        double slot(String key) {
            Integer index = order.get(key);
            if (index == null) order.put(key, index = order.size());
            return Math.min(1, index / 16.0);
        }
    }

    double seconds() {
        return !bool("Slide In") ? 0 : SLIDE_SECONDS * 100 / (getOption("Animation Speed") instanceof SliderOption speed ? Math.max(1, speed.getValue()) : 100);
    }

    /** Pre-1.9.11 versions (26.2): the old style's look, no game data. */
    public void draw(LadsGraphics g, List<String> column, boolean left, int screenWidth, boolean visible) {
        draw(g, column, left, screenWidth, visible, null);
    }

    /**
     * Draws one column of the debug screen (top down; left or right aligned at {@code screenWidth}), as the game's own column would
     * be: from y 2, 9 pixels a line. {@code visible}: the debug screen itself is open (26.x also shows single entries with it closed:
     * those are drawn as they come, without a slide). {@code info}: what the game knows, for the BetterF3 style (null: the old style).
     */
    public void draw(LadsGraphics g, List<String> column, boolean left, int screenWidth, boolean visible, Info info) {
        draw(g, column, left, screenWidth, visible, info, System.nanoTime());
    }

    void draw(LadsGraphics g, List<String> column, boolean left, int screenWidth, boolean visible, Info info, long now) {
        if (!visible) {
            Side plain = new Side();
            plain.lines = arrange(column);
            render(g, plain, left, screenWidth, 1, false);
            return;
        }
        Side side = left ? leftSide : rightSide;
        int intervalMs = getOption("Update Interval") instanceof SliderOption slider ? (int) slider.getValue() : 0;
        long intervalNanos = intervalMs * 1_000_000L;
        if (intervalNanos <= 0 || side.lines.isEmpty() || now - side.lastUpdateNanos >= intervalNanos) {
            side.lines = style() == BETTERF3 && info != null ? betterF3(column, left, info) : arrange(column);
            side.lastUpdateNanos = now;
        }
        side.screenWidth = screenWidth;
        render(g, side, left, screenWidth, side.slide.step(true, now, seconds()), true);
    }

    /** Each frame the debug screen is closed: slides out the columns it last showed. */
    public void drawClosed(LadsGraphics g) { drawClosed(g, System.nanoTime()); }

    void drawClosed(LadsGraphics g, long now) {
        for (Side side : new Side[] {leftSide, rightSide}) {
            if (side.lines.isEmpty()) continue;
            double progress = side.slide.step(false, now, seconds());
            if (progress <= 0) {
                side.lines = List.of();
                side.order.clear();
                side.lastUpdateNanos = 0;
                continue;
            }
            render(g, side, side == leftSide, side.screenWidth, progress, false);
        }
    }

    /** Whether a column is still sliding out (the debug screen closed); one left undrawn for a second has ended. */
    public boolean closing() {
        long now = System.nanoTime();
        return !leftSide.lines.isEmpty() && now - leftSide.slide.last < 1_000_000_000L || !rightSide.lines.isEmpty() && now - rightSide.slide.last < 1_000_000_000L;
    }

    /** Whether the Lads HUD steps aside now (Hide Lads HUD): while the debug screen is open and while its columns slide out. */
    public static boolean hidesLadsHud(boolean debugOpen) {
        return com.thelads.core.config.ModuleManager.getInstance().getModule("BetterF3") instanceof BetterF3Module f3 && f3.isEnabled()
            && f3.bool("Hide Lads HUD") && (debugOpen || f3.closing());
    }

    /** QA (BetterF3Qa): a column's slide progress now, 0 to 1. */
    public double progress(boolean left) { return (left ? leftSide : rightSide).slide.progress; }

    /** QA (BetterF3Qa): the x of each line of a column drawn at its last frame, by key (lines off screen: none). */
    public Map<String, Integer> drawn(boolean left) { return (left ? leftSide : rightSide).drawn; }

    private void render(LadsGraphics g, Side side, boolean left, int screenWidth, double progress, boolean cover) {
        boolean shadow = bool("Text Shadow");
        int background = !bool("Background") ? 0 : getOption("Background Color") instanceof ColorOption color
            ? color.isUseGlobal() ? HudSettings.getInstance().getGlobalBackground() : color.getColor() : 0x90505050;
        side.drawn.clear();
        for (int i = 0, y = 2; i < side.lines.size(); i++, y += 9) {
            Line line = side.lines.get(i);
            if (line.gap()) continue;
            double in = ease(progress, side.slot(line.key()));
            if (in <= 0) continue;
            int width = 0;
            for (Seg seg : line.segs()) width += g.textWidth(seg.text());
            int offset = (int) Math.round((1 - in) * (width + 4));
            int x = left ? 2 - offset : screenWidth - 2 - width + offset;
            side.drawn.put(line.key(), x);
            if (cover) com.thelads.core.client.hud.HudCover.add(com.thelads.core.client.hud.HudCover.DEBUG, x - 1, y - 1, x + width + 1, y + 8);
            if (background >>> 24 != 0) g.fill(x - 1, y - 1, x + width + 1, y + 8, background);
            for (Seg seg : line.segs()) {
                g.drawText(seg.text(), x, y, seg.color(), shadow);
                x += g.textWidth(seg.text());
            }
        }
        if (trace != null && (side == leftSide || side == rightSide) && !side.lines.isEmpty()) {
            Line first = side.lines.get(0), last = side.lines.get(side.lines.size() - 1);
            trace.frame(left, cover, progress, side.drawn.getOrDefault(first.key(), OFF), side.drawn.getOrDefault(last.key(), OFF));
        }
    }

    /** QA (BetterF3Qa): told of every column frame drawn (left, the screen open, its progress, the x of its first and last line). */
    public interface Trace { void frame(boolean left, boolean open, double progress, int firstX, int lastX); }

    /** QA: the tracer while BetterF3Qa runs; null otherwise. */
    public static volatile Trace trace;
    /** A line not drawn this frame (off screen), in a trace. */
    public static final int OFF = -9999;
}
