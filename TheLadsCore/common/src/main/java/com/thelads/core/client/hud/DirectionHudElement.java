package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.Locale;
import java.util.Objects;

public class DirectionHudElement extends TextHudElement {
    private String cachedText, cachedDirection;
    private int cachedYaw, cachedFormat;
    private boolean cachedLongNames;
    public DirectionHudElement() {
        super(60);
        this.x = 5;
        this.y = 125;
        this.width = 80;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        String dir = g.getGame().getPlayerDirection();
        boolean longNames = optBool("Long names", false);
        int format = optCycle("Format", 0);
        float yaw = format == 1 || format == 2 ? g.getGame().getYaw() : Float.NaN;
        int yawBits = Float.floatToIntBits(yaw);
        if (cachedText == null || !Objects.equals(cachedDirection, dir) || cachedLongNames != longNames
                || cachedFormat != format || cachedYaw != yawBits) {
            cachedText = formatDirection(dir, longNames, format, yaw);
            cachedDirection = dir;
            cachedLongNames = longNames;
            cachedFormat = format;
            cachedYaw = yawBits;
        }
        return cachedText;
    }

    private static String formatDirection(String dir, boolean longNames, int format, float yaw) {
        String cardinal = dir == null || dir.isBlank() ? "Unknown" : dir;
        cardinal = switch (cardinal.toLowerCase(Locale.ROOT)) {
            case "n", "north" -> longNames ? "North" : "N";
            case "s", "south" -> longNames ? "South" : "S";
            case "e", "east" -> longNames ? "East" : "E";
            case "w", "west" -> longNames ? "West" : "W";
            default -> cardinal;
        };
        if (format != 1 && format != 2) return cardinal;
        String degrees = "Yaw unavailable";
        if (Float.isFinite(yaw) && yaw != -1.0f) {
            double normalized = ((yaw % 360.0) + 360.0) % 360.0;
            double rounded = (Math.round(normalized * 10.0) / 10.0) % 360.0;
            degrees = String.format(Locale.ROOT, "%.1f°", rounded);
        }
        return switch (format) {
            case 1 -> degrees;
            case 2 -> cardinal + " (" + degrees + ")";
            default -> cardinal;
        };
    }
}
