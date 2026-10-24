package com.thelads.core.client;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.modules.CrosshairModule;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What Crosshair Tweaks' previews (the settings page and the drawing editor) show behind the crosshair: a 26.3 screenshot of a
 * biome at a time of day, a two-colour gradient, a solid colour, the player's own picture, or the live game (in a world; the
 * chosen biome elsewhere). The screenshots ship once, in the common resources, and are read only when drawn.
 */
public final class CrosshairBackground {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    public static final int BIOME = 0, GRADIENT = 1, SOLID = 2, IMAGE = 3, LIVE = 4;
    public static final String[] MODES = {"Biome", "Gradient", "Solid Color", "Your Image", "Live Game"};
    public static final String[] TIMES = {"Sunrise", "Morning", "Noon", "Sunset", "Night", "Midnight"};
    public static final String[] DIRECTIONS = {"Top to Bottom", "Left to Right"};
    /** Every 26.3 biome (the_void aside), as the Background Biome option lists them. Append only: the option saves the index. */
    static final String[] BIOMES = {
        "badlands", "bamboo_jungle", "beach", "birch_forest", "cherry_grove", "cold_ocean", "dappled_forest", "dark_forest",
        "deep_cold_ocean", "deep_frozen_ocean", "deep_lukewarm_ocean", "deep_ocean", "desert", "eroded_badlands", "flower_forest",
        "forest", "frozen_ocean", "frozen_peaks", "frozen_river", "grove", "ice_spikes", "jagged_peaks", "jungle", "lukewarm_ocean",
        "mangrove_swamp", "meadow", "mushroom_fields", "ocean", "old_growth_birch_forest", "old_growth_pine_taiga",
        "old_growth_spruce_taiga", "pale_garden", "plains", "river", "savanna", "savanna_plateau", "snowy_beach", "snowy_plains",
        "snowy_slopes", "snowy_taiga", "sparse_jungle", "stony_peaks", "stony_shore", "sunflower_plains", "swamp", "taiga",
        "warm_ocean", "windswept_forest", "windswept_gravelly_hills", "windswept_hills", "windswept_savanna", "wooded_badlands",
        "deep_dark", "dripstone_caves", "lush_caves", "sulfur_caves",
        "basalt_deltas", "crimson_forest", "nether_wastes", "soul_sand_valley", "warped_forest",
        "the_end", "end_barrens", "end_highlands", "end_midlands", "small_end_islands"};
    /** Cave, Nether and End biomes: one picture each (their light does not follow the time of day). */
    private static final Set<String> TIMELESS = new HashSet<>(Arrays.asList("deep_dark", "dripstone_caves", "lush_caves", "sulfur_caves",
        "basalt_deltas", "crimson_forest", "nether_wastes", "soul_sand_valley", "warped_forest",
        "the_end", "end_barrens", "end_highlands", "end_midlands", "small_end_islands"));
    public static final int DEFAULT_BIOME = Arrays.asList(BIOMES).indexOf("cherry_grove"), DEFAULT_TIME = 2;
    static final String FOLDER = "/assets/theladscore/crosshair_backgrounds/";
    /** Longest side a picture is kept at (your own image is scaled down to it). */
    static final int MAX_SIDE = 1024;
    /** Your own picture: at most 20 MB (as an uploaded sound) and 100 megapixels. */
    static final long MAX_FILE_BYTES = 20L << 20, MAX_PIXELS = 100_000_000L;
    private static final Set<String> UNREADABLE = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    /** The Choose image button's state while the dialog is open or after a bad pick; the picked copy waits for the next frame. */
    private static volatile String status = "", picked;

    private CrosshairBackground() {}

    private static final String[] BIOME_NAMES = computeBiomeNames();

    private static String[] computeBiomeNames() {
        String[] names = new String[BIOMES.length];
        for (int i = 0; i < BIOMES.length; i++) {
            StringBuilder name = new StringBuilder();
            for (String word : BIOMES[i].split("_")) name.append(name.length() == 0 ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            names[i] = name.toString();
        }
        return names;
    }

    /** The biomes' names as the option shows them ("Old Growth Spruce Taiga"). */
    public static String[] biomeNames() {
        return BIOME_NAMES.clone();
    }

    public static String[] biomeIds() { return BIOMES.clone(); }

    public static boolean timeless(int biome) { return TIMELESS.contains(BIOMES[clamp(biome)]); }
    /** By id: the 26.3 capture probe photographs these once, from an air pocket (caves, the Nether) or the ground (the End). */
    public static boolean timeless(String id) { return TIMELESS.contains(id); }

    /** The screenshot of a biome at a time of day, as a resource path. */
    public static String picture(int biome, int time) {
        String id = BIOMES[clamp(biome)];
        return FOLDER + id + (TIMELESS.contains(id) ? "" : "_" + TIMES[Math.max(0, Math.min(TIMES.length - 1, time))].toLowerCase(Locale.ROOT)) + ".jpg";
    }

    private static int clamp(int biome) { return Math.max(0, Math.min(BIOMES.length - 1, biome)); }

    /** The chosen background filling the box; a checker where nothing else can be drawn (a version without pictures). */
    public static void draw(LadsGraphics g, CrosshairModule m, int x, int y, int w, int h) {
        adopt(m);
        int mode = m.background.getIndex();
        if (mode == LIVE && g.drawGameView16x9(x, y, w, h) != null) return;
        if (mode == GRADIENT) { gradient(g, x, y, w, h, color(m.gradientStart), color(m.gradientEnd), m.gradientDirection.getIndex() == 1); return; }
        if (mode == SOLID) { g.fill(x, y, x + w, y + h, color(m.solidColor) | 0xFF000000); return; }
        String own = mode == IMAGE ? ownPicture(m) : null;
        // An unreadable picture is not tried again each frame (the versions keep one texture: it would flip with the biome's).
        if (own != null && g.drawImage(own, x, y, w, h)) return;
        if (g.drawImage(picture(m.biome.getIndex(), m.time.getIndex()), x, y, w, h)) return;
        g.fill(x, y, x + w, y + h, CrosshairDesign.BACKDROP_DARK);
        for (CrosshairDesign.Rect tile : CrosshairDesign.checker(w, h))
            g.fill(x + tile.left(), y + tile.top(), x + tile.right(), y + tile.bottom(), CrosshairDesign.BACKDROP_LIGHT);
    }

    /** The chosen picture's file, or null when none is chosen or it cannot be read (the biome stands in). */
    private static String ownPicture(CrosshairModule m) {
        if (m.image.getValue().isEmpty()) return null;
        String path = imageFile(m.image.getValue()).getPath();
        return UNREADABLE.contains(path) ? null : path;
    }

    /** A colour option's colour; Global follows the HUD's global background colour. */
    private static int color(ColorOption option) {
        return option.isUseGlobal() ? HudSettings.getInstance().getGlobalBackground() : option.getColor();
    }

    /** The settings preview's caption: what the background shows ("" for a plain colour). */
    public static String caption(CrosshairModule m, boolean live) {
        int mode = m.background.getIndex();
        String biome = BIOME_NAMES[clamp(m.biome.getIndex())] + (timeless(m.biome.getIndex()) ? "" : " · " + m.time.getValue());
        if (mode == LIVE) return live ? "Live game" : "Live game in a world · " + biome;
        if (mode == IMAGE) return ownPicture(m) == null ? "Choose your image · " + biome : "Your image";
        return mode == BIOME ? biome : "";
    }

    /** One fill per row (or column): the GUI has no gradient call on every version. */
    static void gradient(LadsGraphics g, int x, int y, int w, int h, int from, int to, boolean across) {
        int steps = across ? w : h;
        for (int i = 0; i < steps; i++) {
            int color = mix(from, to, steps <= 1 ? 0 : i / (double) (steps - 1)) | 0xFF000000;
            if (across) g.fill(x + i, y, x + i + 1, y + h, color);
            else g.fill(x, y + i, x + w, y + i + 1, color);
        }
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int gr = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | gr << 8 | bl;
    }

    /** The part of a picture that fills a box of that shape (centred, nothing stretched): {u, v, width, height} in picture pixels. */
    public static int[] cover(int pictureW, int pictureH, int boxW, int boxH) {
        if (boxW <= 0 || boxH <= 0) return new int[] {0, 0, pictureW, pictureH};
        if ((long) pictureW * boxH > (long) pictureH * boxW) {
            int w = Math.max(1, (int) ((long) pictureH * boxW / boxH));
            return new int[] {(pictureW - w) / 2, 0, w, pictureH};
        }
        int h = Math.max(1, (int) ((long) pictureW * boxH / boxW));
        return new int[] {0, (pictureH - h) / 2, pictureW, h};
    }

    /** A decoded picture: ARGB rows, top first. */
    public static final class Picture {
        public final int width, height;
        public final int[] argb;
        Picture(int width, int height, int[] argb) { this.width = width; this.height = height; this.argb = argb; }
    }

    /**
     * The picture {@link LadsGraphics#drawImage} names: "/assets/..." from the jar, else a file. Scaled down to {@link #MAX_SIDE}.
     * Null (and logged) when it cannot be read; the versions keep only the last one they uploaded.
     */
    public static Picture read(String source) {
        if (source == null || source.isEmpty()) return null;
        boolean resource = source.startsWith("/assets/");
        try (InputStream in = resource ? CrosshairBackground.class.getResourceAsStream(source) : Files.newInputStream(new File(source).toPath())) {
            if (in == null) throw new IOException("missing");
            if (!resource && new File(source).length() > MAX_FILE_BYTES) throw new IOException("over 20 MB");
            BufferedImage image = fit(decode(in));
            int w = image.getWidth(), h = image.getHeight();
            return new Picture(w, h, image.getRGB(0, 0, w, h, null, 0, w));
        } catch (Exception | LinkageError | OutOfMemoryError failure) { // a huge picture must not take the game down
            LOG.warn("Crosshair background {} unavailable: {}", source, failure.toString());
            UNREADABLE.add(source);
            return null;
        }
    }

    /**
     * Decodes a PNG or JPG, refusing more than {@link #MAX_PIXELS} and reading big ones with every n-th pixel (n so that the
     * decoded side stays near 2 x {@link #MAX_SIDE}), so a photo never needs its full size in memory.
     */
    private static BufferedImage decode(InputStream in) throws IOException {
        try (javax.imageio.stream.ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            java.util.Iterator<javax.imageio.ImageReader> readers = stream == null ? null : ImageIO.getImageReaders(stream);
            if (readers == null || !readers.hasNext()) throw new IOException("not a PNG or JPG picture");
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                long w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w * h > MAX_PIXELS) throw new IOException("picture too large (" + w + "x" + h + ")");
                javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
                int every = (int) Math.max(1, Math.max(w, h) / (2L * MAX_SIDE));
                param.setSourceSubsampling(every, every, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage fit(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight(), side = Math.max(w, h);
        if (side <= MAX_SIDE && image.getType() == BufferedImage.TYPE_INT_ARGB) return image;
        double k = Math.min(1, MAX_SIDE / (double) side);
        int tw = Math.max(1, (int) Math.round(w * k)), th = Math.max(1, (int) Math.round(h * k));
        BufferedImage scaled = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    // ---- Your Image ----

    /** Where a chosen picture is kept: a scaled copy in the shared Lads folder, so moving the original does not lose it. */
    public static File imageFile(String name) {
        // Only a file name, so a hand-edited config cannot point the reads (or the delete in adopt) outside the folder.
        return ClientPaths.getSharedDir().resolve("backgrounds").resolve(new File(name).getName()).toFile();
    }

    /** The Choose image button's label: the dialog's state or the last problem, else what to do. */
    public static String chooseLabel(CrosshairModule m) {
        String text = status;
        return !text.isEmpty() ? text : m.image.getValue().isEmpty() ? "Choose image..." : "Change image...";
    }

    /**
     * The file dialog (LadsGameBridge.chooseImageFile, each version's own), then a scaled PNG copy of the pick in the shared folder,
     * off the game thread; the option takes it in the next preview frame ({@link #adopt}).
     */
    public static void choose() {
        status = "Choose a PNG or JPG...";
        LadsGameBridge.get().chooseImageFile().whenComplete((path, failure) -> {
            if (failure != null) LOG.warn("Crosshair background: no file dialog", failure);
            if (failure != null || path == null) status = "";
            else use(path);
        });
    }

    /** A picked file (the dialog's, or QA's): its scaled copy, taken by the option on the next preview frame. */
    public static void use(java.nio.file.Path path) {
        try {
            String name = store(path, "crosshair");
            picked = name;
            status = "";
            LOG.info("Crosshair background: {} from {}", name, path.getFileName());
        } catch (Throwable failure) {
            status = "Can't use that file";
            LOG.warn("Crosshair background: {} unusable", path, failure);
        }
    }

    /** A scaled PNG copy of a picked picture in the shared folder (imageFile), named prefix-time.png; returns that name. */
    public static String store(java.nio.file.Path path, String prefix) throws IOException {
        Picture picture = read(path.toString());
        if (picture == null) throw new IOException("not a PNG or JPG picture");
        BufferedImage copy = new BufferedImage(picture.width, picture.height, BufferedImage.TYPE_INT_ARGB);
        copy.setRGB(0, 0, picture.width, picture.height, picture.argb, 0, picture.width);
        String name = prefix + "-" + System.currentTimeMillis() + ".png";
        File file = imageFile(name);
        Files.createDirectories(file.getParentFile().toPath());
        if (!ImageIO.write(copy, "png", file)) throw new IOException("could not write " + file);
        return name;
    }

    /** On the game thread: a finished pick becomes the option (saved), and the copy it replaces is deleted. */
    private static void adopt(CrosshairModule m) {
        String name = picked;
        if (name == null) return;
        picked = null;
        String old = m.image.getValue();
        m.image.setValue(name);
        m.background.setIndex(IMAGE);
        ConfigManager.save();
        try { if (old.matches("crosshair-[0-9]+[.]png") && !old.equals(name)) Files.deleteIfExists(imageFile(old).toPath()); } // only our own copies
        catch (IOException failure) { LOG.warn("Crosshair background: old copy {} stays", old, failure); }
    }
}
