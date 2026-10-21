package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

public class CoordinatesHudElement extends HudElement {
    /** Where the player faces, from yaw 0 (south) round to the west, in 45 degree steps, with the axes that way points along. */
    private static final String[] COMPASS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] AXES = {"+Z", "-X +Z", "-X", "-X -Z", "-Z", "+X -Z", "+X", "+X +Z"};
    private static final int DIRECTION_GAP = 8;
    private LadsGraphics preparedGraphics;
    private boolean hasPlayer, vertical, showBiome, showC;
    private String px, py, pz, prefix, separator, biomeText, compass, axes, directionText;
    private String pxSep, pySep, biomeVerticalText = "Biome: Plains", biomeHorizText = " (Plains)";
    private String cachedCText = null, cText, cTextHoriz;
    private int lastPosX = Integer.MIN_VALUE, lastPosY = Integer.MIN_VALUE, lastPosZ = Integer.MIN_VALUE;
    private int lastBiomeBx = Integer.MIN_VALUE, lastBiomeBy = Integer.MIN_VALUE, lastBiomeBz = Integer.MIN_VALUE;
    private String cachedBiomeName = null;
    private int lastRenderedChunks = -1, lastMeasuredChunks = -1;
    private int lastFacing = -2, lastFormat = -1;
    private boolean lastVertical, lastShowBiome, lastShowDir, lastShowC;
    private String lastBiomeName = null;
    private Object lastMetricsKey = null;
    private int wPrefix, wPxSep, wPySep, wPz, wBiome, wDir;
    private int wCompass, wAxes, wC, wCHoriz;

    public CoordinatesHudElement() {
        this.x = 5;
        this.y = 25;
        this.width = 110;
        this.height = 16;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        preparedGraphics = g;
        hasPlayer = g.getGame().hasPlayer();
        showBiome = optBool("Show Biome", false);
        boolean showDirection = optBool("Show Direction", true);
        showC = optBool("Show C", false);
        if (!hasPlayer) {
            compass = axes = directionText = cText = cTextHoriz = null;
            lastRenderedChunks = lastMeasuredChunks = -1;
            cachedCText = null;
            lastPosX = Integer.MIN_VALUE;
            lastPosY = Integer.MIN_VALUE;
            lastPosZ = Integer.MIN_VALUE;
            lastBiomeBx = Integer.MIN_VALUE;
            lastBiomeBy = Integer.MIN_VALUE;
            lastBiomeBz = Integer.MIN_VALUE;
            cachedBiomeName = null;
            width = 110; height = Math.max(16, g.fontHeight() + 6);
            return;
        }

        int facing = showDirection ? facing(g.getGame().getYaw(), g.getGame().getPlayerDirection()) : -1;
        int posX = g.getGame().getPlayerX();
        int posY = g.getGame().getPlayerY();
        int posZ = g.getGame().getPlayerZ();
        int format = optCycle("Format", 2);
        vertical = optBool("Vertical", true);
        int chunks = showC ? g.getGame().getRenderedChunks() : -1;
        if (showC) {
            if (chunks != lastRenderedChunks || cachedCText == null) {
                lastRenderedChunks = chunks;
                cachedCText = "C: " + chunks;
            }
            cText = cachedCText;
        } else {
            cText = null;
            cTextHoriz = null;
        }
        String bName = cachedBiomeName;
        if (showBiome && (posX != lastBiomeBx || posY != lastBiomeBy || posZ != lastBiomeBz || bName == null)) {
            lastBiomeBx = posX;
            lastBiomeBy = posY;
            lastBiomeBz = posZ;
            bName = g.getGame().getBiomeName();
            cachedBiomeName = bName;
        }
        Object metricsKey = g.textMetricsKey();

        if (posX == lastPosX && posY == lastPosY && posZ == lastPosZ && facing == lastFacing
                && format == lastFormat && vertical == lastVertical && showBiome == lastShowBiome
                && showDirection == lastShowDir && showC == lastShowC && (!showC || chunks == lastMeasuredChunks) && java.util.Objects.equals(bName, lastBiomeName)
                && java.util.Objects.equals(metricsKey, lastMetricsKey)) {
            return;
        }

        lastPosX = posX; lastPosY = posY; lastPosZ = posZ; lastFacing = facing;
        lastFormat = format; lastVertical = vertical; lastShowBiome = showBiome;
        lastShowDir = showDirection; lastShowC = showC; lastMeasuredChunks = chunks; lastBiomeName = bName; lastMetricsKey = metricsKey;

        compass = axes = directionText = null;
        if (facing >= 0) {
            compass = COMPASS[facing];
            axes = AXES[facing];
        }

        px = (format == 2 ? "X: " : "") + posX;
        py = (format == 2 ? "Y: " : "") + posY;
        pz = (format == 2 ? "Z: " : "") + posZ;
        biomeText = (bName != null && !bName.isEmpty()) ? bName : "Plains";
        biomeVerticalText = "Biome: " + biomeText;

        if (vertical) {
            int lineHeight = g.fontHeight() + 2;
            int maxTextW = Math.max(g.textWidth(px), Math.max(g.textWidth(py), g.textWidth(pz)));
            int rightColW = 0;
            if (compass != null) {
                wCompass = g.textWidth(compass);
                wAxes = g.textWidth(axes);
                rightColW = Math.max(wCompass, wAxes);
            }
            if (cText != null) {
                wC = g.textWidth(cText);
                rightColW = Math.max(rightColW, wC);
            }
            if (rightColW > 0) {
                maxTextW += DIRECTION_GAP + rightColW;
            }
            if (showBiome) {
                maxTextW = Math.max(maxTextW, g.textWidth(biomeVerticalText));
            }
            this.width = Math.max(70, maxTextW + 8);
            this.height = (showBiome ? 4 : 3) * lineHeight + 5;
            return;
        }

        prefix = format == 1 ? "Coords: " : "";
        separator = format == 1 ? ", " : " ";
        pxSep = px + separator;
        pySep = py + separator;
        biomeHorizText = separator + "(" + biomeText + ")";
        wPrefix = prefix.isEmpty() ? 0 : g.textWidth(prefix);
        wPxSep = g.textWidth(pxSep);
        wPySep = g.textWidth(pySep);
        wPz = g.textWidth(pz);
        int horizW = wPrefix + wPxSep + wPySep + wPz;
        if (showBiome) {
            wBiome = g.textWidth(biomeHorizText);
            horizW += wBiome;
        }
        if (compass != null) {
            directionText = separator + compass + " (" + axes + ")";
            wDir = g.textWidth(directionText);
            horizW += wDir;
        }
        if (cText != null) {
            cTextHoriz = separator + cText;
            wCHoriz = g.textWidth(cTextHoriz);
            horizW += wCHoriz;
        } else {
            cTextHoriz = null;
        }
        this.width = Math.max(120, horizW + 8);
        this.height = Math.max(16, g.fontHeight() + 6);
    }

    @Override public void render(LadsGraphics g) {
        if (preparedGraphics != g) prepareRender(g, false);
        preparedGraphics = null;
        drawBackground(g);
        if (!hasPlayer) return;
        boolean perAxis = optBool("Per-axis colors", false);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        int base = resolveColor();
        int cx = perAxis ? optColor("X Color", base) : base;
        int cy = perAxis ? optColor("Y Color", base) : base;
        int cz = perAxis ? optColor("Z Color", base) : base;
        if (vertical) {
            int lineHeight = g.fontHeight() + 2;
            // Force left-aligned
            g.drawText(px, x + 4, y + 3, cx, shadow);
            g.drawText(py, x + 4, y + 3 + lineHeight, cy, shadow);
            g.drawText(pz, x + 4, y + 3 + 2 * lineHeight, cz, shadow);
            if (showBiome) {
                g.drawText(biomeVerticalText, x + 4, y + 3 + 3 * lineHeight, base, shadow);
            }
            if (compass != null) { // on the right, level with the first two lines
                g.drawText(compass, x + width - 4 - wCompass, y + 3, base, shadow);
                g.drawText(axes, x + width - 4 - wAxes, y + 3 + lineHeight, base, shadow);
            }
            if (cText != null) {
                g.drawText(cText, x + width - 4 - wC, y + 3 + 2 * lineHeight, base, shadow);
            }
            return;
        }
        // Force left-aligned
        int tx = x + 4;
        int ty = y + (height - g.fontHeight()) / 2 + 1;
        if (!prefix.isEmpty()) {
            g.drawText(prefix, tx, ty, base, shadow);
            tx += wPrefix;
        }
        g.drawText(pxSep, tx, ty, cx, shadow);
        tx += wPxSep;
        g.drawText(pySep, tx, ty, cy, shadow);
        tx += wPySep;
        g.drawText(pz, tx, ty, cz, shadow);
        tx += wPz;
        if (showBiome) {
            g.drawText(biomeHorizText, tx, ty, base, shadow);
            tx += wBiome;
        }
        if (directionText != null) {
            g.drawText(directionText, tx, ty, base, shadow);
            tx += wDir;
        }
        if (cTextHoriz != null) g.drawText(cTextHoriz, tx, ty, base, shadow);
    }

    /** Which of the eight directions the player faces (an index into COMPASS), by yaw (0 = south), else by the facing's name; -1 when neither says. */
    static int facing(float yaw, String name) {
        if (Float.isFinite(yaw) && yaw != -1f) return (int) (((yaw % 360 + 360) % 360 + 22.5f) / 45) % 8; // -1: the bridge has no yaw
        String facing = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return facing.startsWith("south") ? 0 : facing.startsWith("west") ? 2 : facing.startsWith("north") ? 4 : facing.startsWith("east") ? 6 : -1;
    }

    public String getCText() { return cText; }
    public String getCachedCText() { return cachedCText; }
}
