package com.thelads.core.client.killbanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The game's kill banner FX flipbooks (killbanner/fx.properties + fx/&lt;name&gt;.png atlases, tools/killbanner/game_fx.py):
 * each plays one-shot from the banner's FX event (the mark frame), a frame per 1/fps seconds, its frame stretched into
 * its widget's box. The banner plays the hero flame behind the emblem on every kill, a tier flipbook left (mirrored)
 * and right from two kills (tier 1: 2-3 kills, 2: 4, 3: 5+; per skin in fx-skins.properties, else the Base tiers), and
 * on an ace the large sparks and four X sparks. All tinted the skin's colour.
 */
public final class KillBannerFx {
    /** A flipbook: its atlas cell size and columns, and the cell each played frame shows (-1: blank). */
    public record Book(String name, float fps, int cellW, int cellH, int cols, int[] frames) {
        public String asset() { return "/assets/theladscore/killbanner/fx/" + name + ".png"; }
        public float seconds() { return frames.length / fps; }
        /**
         * The cell {@code seconds} after the FX event, or -1 before the flipbook's first frame, on a blank frame or once it is
         * over. The game's sprite widget shows the first frame a timer tick after the event, not at once: measured against
         * Oni's preview footage, every frame lands {@link #FIRST_TICK} after its event-relative time ({@code frame / fps}).
         */
        public int cell(float seconds) {
            float t = seconds - FIRST_TICK;
            if (t < 0) return -1;
            int f = (int) (t * fps);
            return f < frames.length ? frames[f] : -1;
        }
    }

    /** Seconds from the FX event to a flipbook's first frame: the sprite widget's first timer tick (measured: 3 frames at 60 fps). */
    public static final float FIRST_TICK = .05f;

    /** Boxes (art px) and places (art px from the ring centre) the game gives its FX widgets. */
    public static final float FLAME_W = 199, FLAME_H = 224, FLAME_Y = -30, TIER_SIZE = 256, LARGE_SIZE = 300, X_W = 80, X_H = 250, X_OFFSET = 100;
    private static final String[] BASE_TIERS = {"baset1_fx", "baset2_fx", "baset3_fx"};
    private static final Map<String, Book> BOOKS = new HashMap<>();
    private static final Properties SKINS = new Properties();
    public static final Book FLAME, LARGE_SPARKS, X_SPARKS;

    static {
        Properties data = read("/assets/theladscore/killbanner/fx.properties");
        for (String key : data.stringPropertyNames()) {
            if (!key.endsWith(".fps")) continue;
            String name = key.substring(0, key.length() - 4);
            String[] cell = data.getProperty(name + ".cell", "256,256").split(",");
            String[] frames = data.getProperty(name + ".frames", "").split(",");
            int[] cells = new int[frames.length];
            for (int i = 0; i < frames.length; i++) cells[i] = Integer.parseInt(frames[i].trim());
            BOOKS.put(name, new Book(name, Float.parseFloat(data.getProperty(key).trim()), Integer.parseInt(cell[0].trim()),
                Integer.parseInt(cell[1].trim()), Integer.parseInt(data.getProperty(name + ".cols", "1").trim()), cells));
        }
        SKINS.putAll(read("/assets/theladscore/killbanner/fx-skins.properties"));
        FLAME = book("fb_heroflame");
        LARGE_SPARKS = book("fb_large_sparks");
        X_SPARKS = book("fb_x_sparks");
    }

    private KillBannerFx() {}

    private static Properties read(String path) {
        Properties data = new Properties();
        try (InputStream in = KillBannerFx.class.getResourceAsStream(path)) {
            if (in != null) data.load(in);
        } catch (IOException ignored) {
        }
        return data;
    }

    /** A flipbook by its name, or null. */
    public static Book book(String name) {
        return BOOKS.get(name);
    }

    /** The tier flipbook the skin plays left and right for this many kills (none for one kill). */
    public static Book tier(KillBannerStyle style, int variant, int kills) {
        if (kills < 2) return null;
        int tier = kills >= 5 ? 2 : kills >= 4 ? 1 : 0;
        String[] names = BASE_TIERS;
        String own = style == null ? null : SKINS.getProperty(style.id);
        if (own != null) {
            String[] perVariant = own.split("\\|", -1);
            String v = perVariant[Math.max(0, Math.min(perVariant.length - 1, variant))].trim();
            if (!v.isEmpty()) names = v.split(",");
        }
        return book(names[Math.min(tier, names.length - 1)].trim());
    }
}
