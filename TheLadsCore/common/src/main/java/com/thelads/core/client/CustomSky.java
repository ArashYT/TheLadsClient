package com.thelads.core.client;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Custom Skies: the OptiFine/MCPatcher sky format that resource packs ship, for the versions and paths that have nothing reading it
 * (1.8.9 with Ionium, 1.20.1, 26.x; OptiFine reads it itself). A pack adds {@code optifine/sky/world0/skyN.properties} (or the older
 * {@code mcpatcher/sky/world0/}), N = 1, 2, 3 ..., each a layer drawn over the overworld's sky, in order, before the sun and moon:
 * a texture of six faces in a 3x2 grid (bottom, top, east / south, west, north as {@link #FACES} places them), faded in and out by
 * the time of day, the weather and a blend mode, turning with the sun. Supported keys: source, startFadeIn, endFadeIn, startFadeOut,
 * endFadeOut, blend, rotate, speed, axis, weather. Not read: days, daysLoop, biomes, heights, transition, and sun/moon replacement.
 * Each version loads the layers on resource reload and draws them; this class has the format and the numbers.
 */
public final class CustomSky {
    /** Where packs keep the overworld's layers; the first folder that has any wins. */
    public static final String[] FOLDERS = {"optifine/sky/world0/", "mcpatcher/sky/world0/"};
    /** Layers looked for per folder (sky1 .. sky64). */
    public static final int MAX_LAYERS = 64;

    private static final int ZERO = 0, ONE = 1, SRC_COLOR = 0x300, ONE_MINUS_SRC_COLOR = 0x301, SRC_ALPHA = 0x302,
        ONE_MINUS_SRC_ALPHA = 0x303, DST_COLOR = 0x306, ONE_MINUS_DST_COLOR = 0x307;

    /**
     * A layer's blend mode as GL blend factors (OpenGL's numbers; REPLACE draws without blending), and how its brightness reaches the
     * color the texture is drawn with: as alpha (alpha, add, replace), as both (multiply) or as the color (the rest).
     */
    public enum Blend {
        ALPHA(SRC_ALPHA, ONE_MINUS_SRC_ALPHA), ADD(SRC_ALPHA, ONE), SUBTRACT(ONE_MINUS_DST_COLOR, ZERO), MULTIPLY(DST_COLOR, ONE_MINUS_SRC_ALPHA),
        DODGE(ONE, ONE), BURN(ZERO, ONE_MINUS_SRC_COLOR), SCREEN(ONE, ONE_MINUS_SRC_COLOR), OVERLAY(DST_COLOR, SRC_COLOR), REPLACE(ONE, ZERO);

        public final int source, destination;

        Blend(int source, int destination) {
            this.source = source;
            this.destination = destination;
        }

        /** Fills {@code rgba} with the draw color for this brightness. */
        public void color(float brightness, float[] rgba) {
            boolean asAlpha = this == ALPHA || this == ADD || this == REPLACE;
            float c = asAlpha ? 1 : brightness;
            rgba[0] = c;
            rgba[1] = c;
            rgba[2] = c;
            rgba[3] = asAlpha || this == MULTIPLY ? brightness : 1;
        }
    }

    /**
     * The cube, radius 100, in the frame the sky is drawn in after vanilla's turn of -90 degrees about Y: 6 faces x 4 corners of
     * x, y, z, u, v. Face i takes cell (i % 3, i / 3) of the texture's 3x2 grid.
     */
    public static final float[] FACES = {
        -100, -100, 100, 0, 0,          100, -100, 100, 0, .5F,        100, -100, -100, 1 / 3F, .5F,  -100, -100, -100, 1 / 3F, 0,
        100, 100, 100, 1 / 3F, 0,       -100, 100, 100, 1 / 3F, .5F,   -100, 100, -100, 2 / 3F, .5F,  100, 100, -100, 2 / 3F, 0,
        100, 100, -100, 2 / 3F, 0,      100, -100, -100, 2 / 3F, .5F,  100, -100, 100, 1, .5F,        100, 100, 100, 1, 0,
        100, 100, 100, 0, .5F,          100, -100, 100, 0, 1,          -100, -100, 100, 1 / 3F, 1,    -100, 100, 100, 1 / 3F, .5F,
        -100, 100, 100, 1 / 3F, .5F,    -100, -100, 100, 1 / 3F, 1,    -100, -100, -100, 2 / 3F, 1,   -100, 100, -100, 2 / 3F, .5F,
        -100, 100, -100, 2 / 3F, .5F,   -100, -100, -100, 2 / 3F, 1,   100, -100, -100, 1, 1,         100, 100, -100, 1, .5F};

    private CustomSky() {}

    /** One sky layer as a pack describes it. */
    public static final class Layer {
        /** The texture, "namespace:path". */
        public final String texture;
        public final Blend blend;
        public final boolean rotate;
        public final float speed;
        /** The turn axis in the draw frame (the pack's axis, whose default 0 0 1 is the sun's, mapped onto it). */
        public final float axisX, axisY, axisZ;
        final int startFadeIn, endFadeIn, startFadeOut, endFadeOut;
        final boolean fades, clear, rain, thunder;

        Layer(String texture, Blend blend, boolean rotate, float speed, float[] axis, int[] fade, boolean clear, boolean rain, boolean thunder) {
            this.texture = texture;
            this.blend = blend;
            this.rotate = rotate;
            this.speed = speed;
            axisX = axis[0];
            axisY = axis[1];
            axisZ = axis[2];
            fades = fade != null;
            startFadeIn = fades ? fade[0] : 0;
            endFadeIn = fades ? fade[1] : 0;
            startFadeOut = fades ? fade[2] : 0;
            endFadeOut = fades ? fade[3] : 0;
            this.clear = clear;
            this.rain = rain;
            this.thunder = thunder;
        }

        /** How strongly the layer shows (0-1) at this day time (ticks) and weather (rain and thunder 0-1). */
        public float brightness(long dayTime, float rainLevel, float thunderLevel) {
            float weather = (clear ? 1 - rainLevel : 0) + (rain ? rainLevel - thunderLevel : 0) + (thunder ? thunderLevel : 0);
            return Math.max(0, Math.min(1, weather)) * fade((int) Math.floorMod(dayTime, 24000L));
        }

        /** The degrees the layer has turned for this celestial angle (0-1 over a day, as the sun). */
        public float angle(float celestialAngle) {
            return rotate ? celestialAngle * 360F * speed : 0;
        }

        float fade(int time) {
            if (!fades) return 1;
            if (within(time, startFadeIn, endFadeIn)) return span(startFadeIn, time) / (float) Math.max(1, span(startFadeIn, endFadeIn));
            if (within(time, endFadeIn, startFadeOut)) return 1;
            if (within(time, startFadeOut, endFadeOut)) return 1 - span(startFadeOut, time) / (float) Math.max(1, span(startFadeOut, endFadeOut));
            return 0;
        }

        /** Ticks from a to b going forwards round the day. */
        private static int span(int a, int b) {
            return Math.floorMod(b - a, 24000);
        }

        private static boolean within(int time, int from, int to) {
            return span(from, time) <= span(from, to);
        }
    }

    /**
     * The overworld's layers from the resource packs, in order. {@code read} gives a file's text ("namespace:path", null when it is not
     * there), {@code exists} whether a texture is there.
     */
    public static List<Layer> load(Function<String, String> read, Predicate<String> exists) {
        for (String folder : FOLDERS) {
            List<Layer> layers = new ArrayList<>();
            for (int n = 1; n <= MAX_LAYERS; n++) {
                String text = read.apply("minecraft:" + folder + "sky" + n + ".properties");
                Layer layer = text == null ? null : parse(text, folder, n, exists);
                if (layer != null) layers.add(layer);
            }
            if (!layers.isEmpty()) return layers;
        }
        return Collections.emptyList();
    }

    /**
     * One {@code skyN.properties} of {@code folder}; null when it names no usable texture. {@code exists} tells whether a texture path
     * is in the resource packs (the default {@code skyN.png} beside it is used when there is no source).
     */
    public static Layer parse(String text, String folder, int n, Predicate<String> exists) {
        Properties p = new Properties();
        try {
            p.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException broken) {
            return null;
        }
        String texture = texture(p.getProperty("source"), folder, n);
        if (texture == null || !exists.test(texture)) return null;
        int[] fade = null;
        int startIn = time(p.getProperty("startFadeIn")), endIn = time(p.getProperty("endFadeIn")), startOut = time(p.getProperty("startFadeOut"));
        if (startIn >= 0 && endIn >= 0 && startOut >= 0) {
            int endOut = time(p.getProperty("endFadeOut"));
            fade = new int[]{startIn, endIn, startOut, endOut >= 0 ? endOut : Math.floorMod(startOut + Math.floorMod(endIn - startIn, 24000), 24000)};
        }
        boolean clear = true, rain = false, thunder = false;
        String weather = p.getProperty("weather");
        if (weather != null && !weather.trim().isEmpty()) {
            String w = " " + weather.toLowerCase(Locale.ROOT).replace(',', ' ') + " ";
            clear = w.contains(" clear ");
            rain = w.contains(" rain ");
            thunder = w.contains(" thunder ");
        }
        return new Layer(texture, blend(p.getProperty("blend")), !"false".equalsIgnoreCase(trim(p.getProperty("rotate"))),
            number(p.getProperty("speed"), 1), axis(p.getProperty("axis")), fade, clear, rain, thunder);
    }

    /** The texture a source names ("./x.png" beside the properties, "~/x" under optifine/, "ns:path", or a path in minecraft). */
    static String texture(String source, String folder, int n) {
        String s = trim(source);
        if (s == null || s.isEmpty()) return "minecraft:" + folder + "sky" + n + ".png";
        if (s.startsWith("./")) return "minecraft:" + folder + s.substring(2);
        if (s.startsWith("~/")) return "minecraft:optifine/" + s.substring(2);
        if (s.indexOf(':') >= 0) return s.toLowerCase(Locale.ROOT).equals(s) ? s : null;
        if (s.startsWith("/")) s = s.substring(1);
        if (s.startsWith("assets/minecraft/")) s = s.substring("assets/minecraft/".length());
        return s.indexOf('/') < 0 ? "minecraft:" + folder + s : "minecraft:" + s;
    }

    /** "hh:mm" as day ticks (06:00 = 0), or -1. */
    static int time(String value) {
        String s = trim(value);
        if (s == null) return -1;
        int colon = s.indexOf(':');
        try {
            int hours = Integer.parseInt(colon < 0 ? s : s.substring(0, colon)), minutes = colon < 0 ? 0 : Integer.parseInt(s.substring(colon + 1));
            if (hours < 0 || hours > 24 || minutes < 0 || minutes > 59) return -1;
            return Math.floorMod(hours * 1000 + minutes * 1000 / 60 - 6000, 24000);
        } catch (NumberFormatException notTime) {
            return -1;
        }
    }

    static Blend blend(String value) {
        String s = trim(value);
        if (s != null) for (Blend b : Blend.values()) if (b.name().equalsIgnoreCase(s)) return b;
        return Blend.ADD;
    }

    /** The pack's "x y z" (default 0 0 1, the sun's axis) in the draw frame: (z, y, -x). */
    static float[] axis(String value) {
        float x = 0, y = 0, z = 1;
        String s = trim(value);
        if (s != null) {
            String[] parts = s.split("\\s+");
            if (parts.length == 3) {
                try {
                    x = Float.parseFloat(parts[0]);
                    y = Float.parseFloat(parts[1]);
                    z = Float.parseFloat(parts[2]);
                } catch (NumberFormatException keepDefault) {
                    x = 0; y = 0; z = 1;
                }
            }
        }
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-6F) return new float[]{1, 0, 0};
        return new float[]{z / length, y / length, -x / length};
    }

    private static float number(String value, float fallback) {
        String s = trim(value);
        if (s == null) return fallback;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException notNumber) {
            return fallback;
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
