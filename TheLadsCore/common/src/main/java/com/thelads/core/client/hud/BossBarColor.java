package com.thelads.core.client.hud;

/**
 * 1.9+ boss bar color palette and mapping from Minecraft chat/formatting color codes.
 *
 * <p>Colors correspond to Minecraft 1.9's {@code BossEvent.BossBarColor} (pink, blue, red, green,
 * yellow, purple, white) and their textures in {@code textures/gui/bars.png}.
 */
public enum BossBarColor {
    PINK("pink", 0xFFB90090, 0.725f, 0.0f, 0.565f),
    BLUE("blue", 0xFF008FB9, 0.0f, 0.561f, 0.725f),
    RED("red", 0xFFB92A00, 0.725f, 0.165f, 0.0f),
    GREEN("green", 0xFF16B900, 0.086f, 0.725f, 0.0f),
    YELLOW("yellow", 0xFFB6B900, 0.714f, 0.725f, 0.0f),
    PURPLE("purple", 0xFF6100B9, 0.380f, 0.0f, 0.725f),
    WHITE("white", 0xFFB9B9B9, 0.725f, 0.725f, 0.725f);

    private final String name;
    private final int rgb;
    private final float r;
    private final float g;
    private final float b;

    BossBarColor(String name, int rgb, float r, float g, float b) {
        this.name = name;
        this.rgb = rgb;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public String colorName() {
        return name;
    }

    public int rgb() {
        return rgb;
    }

    public float r() {
        return r;
    }

    public float g() {
        return g;
    }

    public float b() {
        return b;
    }

    /**
     * Maps a formatting code character to a boss bar color.
     *
     * <p>Mappings per 1.9+ palette:
     * <ul>
     *   <li>§c, §4 -> RED</li>
     *   <li>§e, §6 -> YELLOW</li>
     *   <li>§a, §2 -> GREEN</li>
     *   <li>§b, §9, §3, §1 -> BLUE</li>
     *   <li>§d -> PINK</li>
     *   <li>§5 -> PURPLE</li>
     *   <li>§f, §7, §8 -> WHITE</li>
     * </ul>
     *
     * @param code character following '§' (or '&amp;')
     * @return the matching {@link BossBarColor}, or {@code null} if none
     */
    public static BossBarColor fromCode(char code) {
        switch (code) {
            case 'c':
            case 'C':
            case '4':
                return RED;
            case 'e':
            case 'E':
            case '6':
                return YELLOW;
            case 'a':
            case 'A':
            case '2':
                return GREEN;
            case 'b':
            case 'B':
            case '9':
            case '3':
            case '1':
                return BLUE;
            case 'd':
            case 'D':
                return PINK;
            case '5':
                return PURPLE;
            case 'f':
            case 'F':
            case '7':
            case '8':
                return WHITE;
            default:
                return null;
        }
    }

    /**
     * Extracts the first boss bar color from formatted text.
     *
     * <p>Finds the first '§' (or '&amp;') followed by a valid color code character,
     * skipping style codes like bold (§l) or reset (§r) until a color code is encountered.
     *
     * @param text formatted boss name (may be null)
     * @return the first {@link BossBarColor}, or {@code null} for vanilla look
     */
    public static BossBarColor fromFormattedName(String text) {
        if (text == null) return null;
        int len = text.length();
        for (int i = 0; i < len - 1; i++) {
            char c = text.charAt(i);
            if (c == '§' || c == '&') {
                BossBarColor color = fromCode(text.charAt(i + 1));
                if (color != null) {
                    return color;
                }
            }
        }
        return null;
    }
}
