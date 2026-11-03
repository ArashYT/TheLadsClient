package com.thelads.core.client.title;

import com.thelads.core.LadsVersion;
import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.LadsNetworkStatus;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.title.TitleScreenTheme.Rect;
import com.thelads.core.modules.TitleScaleModule;
import java.util.ArrayList;
import java.util.List;

/**
 * The default title layout, in the site's Voxel Noir: the real panorama as the backdrop, a hard dark slab down the left whose edge
 * breaks into blocks, a voxel wordmark, Singleplayer and Multiplayer as the two loud buttons, everything else as quiet text, and one
 * status strip. All versions share this layout and drawing; TitleScreenTheme keeps the legacy layout.
 *
 * <p>While a version draws this layout's frame (beginFrame to endFrame, render thread), TitleScreenTheme.renderButton and
 * renderCompactButton draw through here, so the versions' widget code stays as it is.
 */
public final class TitleNoir {
    /** Colours of ladsclient-site/public/style.css. */
    private static final int BG = 0xFF0B0708, PANEL = 0xFF160D10, PANEL_ALT = 0xFF211217, TEXT = 0xFFFFF8F8, MUTED = 0xFFC6B2B8,
        RED = 0xFFCC1427, RED_HOVER = 0xFFE01C31, RED_DEEP = 0xFF59050F, RED_TEXT = 0xFFFF5C70, LINE = 0xFF422029,
        LINE_ACCENT = 0xFFFF2438, EDGE = 0xFF050304, GREEN = 0xFF4ADE80, FOCUS = 0xFFFF8395, BEVEL = 0x73FF8291, DISABLED = 0xFF6F6168;
    private static final int SLAB = 0xF00B0708;
    /** The smallest canvas the layout is made for; smaller windows draw it scaled down. */
    public static final int MIN_WIDTH = 320, MIN_HEIGHT = 240;
    private static final int CELL = 6;
    private static final int[] BLOCK_CHANCE = {78, 46, 20, 7};

    private static final String[][] LADS = {
        {"##...", "##...", "##...", "##...", "##...", "##...", "#####"},
        {".###.", "##.##", "##.##", "##.##", "#####", "##.##", "##.##"},
        {"####.", "##.##", "##.##", "##.##", "##.##", "##.##", "####."},
        {".####", "##...", "##...", ".###.", "...##", "...##", "####."}};
    private static final String[][] CLIENT = {
        {".####", "##...", "##...", "##...", "##...", "##...", ".####"},
        {"##...", "##...", "##...", "##...", "##...", "##...", "#####"},
        {"##", "##", "##", "##", "##", "##", "##"},
        {"#####", "##...", "##...", "####.", "##...", "##...", "#####"},
        {"##..#", "###.#", "#####", "##.##", "##.##", "##.##", "##.##"},
        {"#####", ".###.", ".###.", ".###.", ".###.", ".###.", ".###."}};

    public enum Tier { PRIMARY, HERO, NETWORK, LINK }

    /** Canvas size, the slab, the status strip's top, the wordmark's block sizes and top, and each button's place and tier. */
    public record Layout(int width, int height, int railWidth, int stripTop, int block, int clientBlock, int wordY,
                         boolean tagline, List<Rect> buttons, List<Tier> tiers) {}

    private static Layout frame;
    private static volatile Rect lastNetworkPill;

    private TitleNoir() {}

    /** Bounds of the live network status pill drawn in the bottom strip on the most recent frame, or null when hidden. */
    public static Rect lastNetworkPill() {
        return lastNetworkPill;
    }

    /** True when the given canvas coordinate hits the bottom strip's live network status pill (and not in modpack mode). */
    public static boolean clickNetworkPill(double x, double y) {
        Rect pill = lastNetworkPill;
        return LadsNetwork.titleButton() && pill != null
            && x >= pill.x() && y >= pill.y() && x < pill.x() + pill.width() && y < pill.y() + pill.height();
    }

    /**
     * Window pixels per canvas pixel: a whole number whenever the window allows (so the voxels and the font stay pixel-true),
     * at about 360 canvas pixels high, at least 2 while the window holds the minimum canvas that way; independent of GUI scale.
     */
    public static double windowPerCanvasPixel(int windowWidth, int windowHeight) {
        if (windowWidth <= 0 || windowHeight <= 0) return 1;
        int scale = Math.min(Math.max(2, Math.round(windowHeight / 360f)), Math.min(windowWidth / MIN_WIDTH, windowHeight / MIN_HEIGHT));
        return scale >= 1 ? scale : Math.min((double) windowWidth / MIN_WIDTH, (double) windowHeight / MIN_HEIGHT);
    }

    /**
     * Buttons in the versions' order: the first (at most two, and only those before the network entry) are the loud ones, the
     * entry at {@code networkIndex} (-1: none) is the network row, the rest are text links in columns above the strip.
     */
    public static Layout layout(int width, int height, int count, int networkIndex) {
        width = Math.max(MIN_WIDTH, width);
        height = Math.max(MIN_HEIGHT, height);
        count = Math.max(0, count);
        int rail = Math.max(200, Math.min(280, width * 38 / 100));
        int x = 12, inner = rail - x - 10;
        int strip = height >= 280 ? 18 : 16, stripTop = height - strip;
        int heroHeight = height >= 330 ? 28 : height >= 280 ? 24 : 22, gap = height >= 280 ? 4 : 2;
        int networkHeight = height >= 280 ? 18 : 16, rowHeight = height >= 330 ? 14 : 12;
        int heroes = Math.min(count, networkIndex >= 0 ? Math.min(2, networkIndex) : 2);
        boolean network = networkIndex >= 0 && networkIndex == heroes && networkIndex < count;
        int links = count - heroes - (network ? 1 : 0);
        int above = heroes * (heroHeight + gap) + (network ? networkHeight + gap : 0) + (links > 0 ? 3 : 0);

        int columns = 2, rows = (links + columns - 1) / columns;
        // Many mods' buttons: more columns before the wordmark would have no room.
        while (columns < 4 && stripTop - 5 - rows * rowHeight - above < 55) {
            columns++;
            rows = (links + columns - 1) / columns;
        }
        int linksTop = stripTop - 5 - rows * rowHeight;
        int top = linksTop - above;

        List<Rect> buttons = new ArrayList<>(count);
        List<Tier> tiers = new ArrayList<>(count);
        int y = top;
        for (int i = 0; i < heroes; i++) {
            buttons.add(new Rect(x, y, inner, heroHeight));
            tiers.add(i == 0 ? Tier.PRIMARY : Tier.HERO);
            y += heroHeight + gap;
        }
        if (network) {
            buttons.add(new Rect(x, y, inner, networkHeight));
            tiers.add(Tier.NETWORK);
        }
        int columnWidth = (inner - (columns - 1) * 4) / columns;
        for (int i = 0; i < links; i++) {
            buttons.add(new Rect(x + (i % columns) * (columnWidth + 4), linksTop + (i / columns) * rowHeight, columnWidth, rowHeight));
            tiers.add(Tier.LINK);
        }

        int wordY = height >= 300 ? 18 : 6, room = top - wordY - 4;
        int block = 3;
        for (int b = 10; b > 3; b--) {
            if (wordWidth(LADS, b) <= inner && wordWidth(CLIENT, clientBlock(b)) <= inner && wordHeight(b) <= room) {
                block = b;
                break;
            }
        }
        return new Layout(width, height, rail, stripTop, block, clientBlock(block), wordY, room >= wordHeight(block) + 14,
            List.copyOf(buttons), List.copyOf(tiers));
    }

    private static int clientBlock(int block) { return Math.max(2, block * 2 / 3); }
    private static int depth(int block) { return Math.max(2, block / 2); }
    /** The whole wordmark, two lines with their extruded sides. */
    public static int wordHeight(int block) {
        int small = clientBlock(block);
        return 7 * block + depth(block) + 4 + 7 * small + depth(small);
    }
    private static int wordWidth(String[][] word, int block) {
        int cells = -1;
        for (String[] letter : word) cells += letter[0].length() + 1;
        return cells * block + 2;
    }

    public static void beginFrame(Layout layout) { frame = layout; }
    public static void endFrame() { frame = null; }

    /** The panorama, drawn by the version before this, stays visible: the slab, the wordmark, the strip. Returns Switch's place (null without it). */
    public static Rect renderBackdrop(LadsGraphics g, Layout l, String username, String version, boolean withSwitch) {
        renderSlab(g, l);
        renderWordmark(g, l);
        return renderStrip(g, l, username, version, withSwitch, LadsNetwork.titleButton() ? networkText() : null);
    }

    /** What the network row and the strip say. */
    public static String networkText() {
        if (!LadsNetworkStatus.online()) return "offline";
        int players = LadsNetworkStatus.players();
        return players >= 0 ? players + " online" : "online";
    }

    private static void renderSlab(LadsGraphics g, Layout l) {
        int w = l.width(), bottom = l.stripTop(), rail = l.railWidth();
        g.fill(0, 0, w, bottom, 0x330B0708);
        g.fill(0, 0, rail, bottom, SLAB);
        // The site's faint dot grid.
        int step = Math.max(16, bottom / 24);
        for (int y = 8; y < bottom; y += step)
            for (int x = 8; x < rail; x += step) g.fill(x, y, x + 1, y + 1, 0x24FF3C50);
        // The slab's edge breaks up into blocks, fewer with each column; fixed, never animated.
        for (int y = 0; y < bottom; y += CELL) {
            int row = y / CELL, end = Math.min(bottom, y + CELL);
            for (int column = 0; column < BLOCK_CHANCE.length; column++) {
                int hash = (row * 73 + column * 151 + row * row * 31 + (row >> 2) * 17) % 100;
                if (hash >= BLOCK_CHANCE[column]) continue;
                int x = rail + column * CELL;
                g.fill(x, y, x + CELL, end, column == 0 && hash % 9 == 0 ? RED_DEEP : SLAB);
            }
        }
        g.fill(0, 0, 3, bottom, RED);
    }

    private static void renderWordmark(LadsGraphics g, Layout l) {
        int x = 13, y = l.wordY(), height = wordHeight(l.block()) + (l.tagline() ? 14 : 0);
        int below = l.buttons().isEmpty() ? l.stripTop() : l.buttons().get(0).y();
        float scale = Math.max(1f, Math.min(TitleScaleModule.getScale(), Math.min(
            (l.railWidth() - 10f - x) / Math.max(1, wordWidth(LADS, l.block())), (below - 4f - y) / height)));
        g.pushPose();
        g.translate(x, y);
        g.scale(scale, scale);
        voxelWord(g, LADS, 0, 0, l.block());
        voxelWord(g, CLIENT, 0, 7 * l.block() + depth(l.block()) + 4, l.clientBlock());
        if (l.tagline()) g.drawText("Is the sever up?", 1, wordHeight(l.block()) + 5, RED_TEXT, false);
        g.popPose();
    }

    /** Block letters as on the site's logo: dark red faces with a bright rim on a hard extruded side and a black outline. */
    private static void voxelWord(LadsGraphics g, String[][] word, int x, int y, int b) {
        int depth = depth(b), o = Math.max(1, b / 4), rim = Math.max(1, b / 4);
        for (int pass = 0; pass < 3; pass++) {
            int cx = x;
            for (String[] letter : word) {
                for (int row = 0; row < letter.length; row++) {
                    for (int col = 0; col < letter[row].length(); col++) {
                        if (letter[row].charAt(col) != '#') continue;
                        int px = cx + col * b, py = y + row * b;
                        if (pass == 0) g.fill(px - o, py - o, px + b + o, py + b + depth + o, EDGE);
                        else if (pass == 1) g.fill(px, py + b, px + b, py + b + depth, 0xFF3A0309);
                        else {
                            g.fill(px, py, px + b, py + b, mix(0xFF7E0310, 0xFF9E0816, (float) row / (letter.length - 1)));
                            if (!filled(letter, row - 1, col)) g.fill(px, py, px + b, py + rim, LINE_ACCENT);
                            if (!filled(letter, row + 1, col)) g.fill(px, py + b - rim, px + b, py + b, LINE_ACCENT);
                            if (!filled(letter, row, col - 1)) g.fill(px, py, px + rim, py + b, LINE_ACCENT);
                            if (!filled(letter, row, col + 1)) g.fill(px + b - rim, py, px + b, py + b, LINE_ACCENT);
                        }
                    }
                }
                cx += (letter[0].length() + 1) * b;
            }
        }
    }

    private static boolean filled(String[] letter, int row, int col) {
        return row >= 0 && row < letter.length && col >= 0 && col < letter[row].length() && letter[row].charAt(col) == '#';
    }

    /** The bottom strip: account and Switch at the left, network and build at the right. {@code network}: its text, null for none. */
    public static Rect renderStrip(LadsGraphics g, Layout l, String username, String version, boolean withSwitch, String network) {
        int w = l.width(), h = l.height(), top = l.stripTop(), strip = h - top;
        g.fill(0, top, w, h, BG);
        g.fill(0, top, w, top + 1, LINE);
        int textY = top + (strip - g.fontHeight()) / 2 + 1, iconX = 8, nameX = iconX + 13;
        String name = username == null ? "Player" : username;
        int square = strip - 2, labelled = g.textWidth(TitleScreenTheme.SWITCH) + TitleScreenTheme.COMPACT_PADDING;
        int networkWidth = network == null ? 0 : 8 + g.textWidth(network) + 10;
        String build = LadsVersion.clientName() + " (" + version + ")";
        if (nameX + Math.min(g.textWidth(name), 72) + 4 + (withSwitch ? labelled : 0) + networkWidth + g.textWidth(build) + 12 > w) build = version;
        int buildX = w - 6 - g.textWidth(build), networkX = buildX - networkWidth;
        int room = networkX - nameX - 6;
        int switchWidth = !withSwitch ? 0 : room - labelled >= 48 ? labelled : square;
        String shown = TitleScreenTheme.fit(g, name, room - (withSwitch ? switchWidth + 4 : 0));
        TitleScreenTheme.icon(g, "user", iconX, top + (strip - 10) / 2 + 1, 9, RED_TEXT);
        g.drawText(shown, nameX, textY, TEXT, false);
        Rect switchRect = withSwitch ? new Rect(nameX + g.textWidth(shown) + 4, top + 1, switchWidth, square) : null;
        if (network != null) {
            boolean up = !"offline".equals(network);
            g.fill(networkX, textY + 1, networkX + 5, textY + 6, up ? GREEN : DISABLED);
            g.drawText(network, networkX + 8, textY, up ? TEXT : MUTED, false);
            int pillW = Math.max(8, networkWidth - 4);
            lastNetworkPill = networkX >= 0 && networkX + pillW <= w ? new Rect(networkX, top + 1, pillW, square) : null;
        } else {
            lastNetworkPill = null;
        }
        g.drawText(build, buildX, textY, MUTED, false);
        return switchRect;
    }

    /** During this layout's frame: draws a title button by the tier of its place. False: not this layout's button, or no frame. */
    static boolean renderButton(LadsGraphics g, int x, int y, int width, int height, String label, String icon,
                                boolean focused, boolean active, float hover) {
        if (frame == null) return false;
        int index = frame.buttons().indexOf(new Rect(x, y, width, height));
        if (index < 0) return false;
        switch (frame.tiers().get(index)) {
            case PRIMARY -> hero(g, x, y, width, height, label, icon, true, focused, active, hover);
            case HERO -> hero(g, x, y, width, height, label, icon, false, focused, active, hover);
            case NETWORK -> networkRow(g, x, y, width, height, label, icon, focused, active, hover);
            default -> link(g, x, y, width, height, label, icon, focused, active, hover);
        }
        return true;
    }

    private static void hero(LadsGraphics g, int x, int y, int w, int h, String label, String icon, boolean primary, boolean focused, boolean active, float hover) {
        int lip = 3, faceHeight = h - lip, faceBottom = y + faceHeight;
        if (focused && active) outline(g, x - 1, y - 1, w + 2, h + 2, FOCUS);
        if (!active) {
            g.fill(x, y, x + w, y + h, PANEL);
        } else if (primary) {
            g.fill(x, y, x + w, faceBottom, mix(RED, RED_HOVER, hover));
            g.fill(x, faceBottom, x + w, y + h, RED_DEEP);
            g.fill(x, y, x + w, y + 1, BEVEL);
        } else {
            g.fill(x, y, x + w, faceBottom, mix(PANEL_ALT, 0xFF34161E, hover));
            g.fill(x, faceBottom, x + w, y + h, EDGE);
            outline(g, x, y, w, faceHeight, hover > .5f ? LINE_ACCENT : LINE);
            g.fill(x, y, x + 4, faceBottom, RED);
        }
        int color = active ? TEXT : DISABLED;
        int textX = x + 11, room = w - 11 - 16;
        if (icon != null) { // before the label: as tall as the doubled text where that fits, else a small one
            int big = 2 * (g.fontHeight() - 1), size = faceHeight >= big && 2 * g.textWidth(label) + big + 6 <= room ? big : Math.min(10, faceHeight - 4);
            TitleScreenTheme.icon(g, icon, textX, y + (faceHeight - size) / 2, size, color);
            textX += size + (size == big ? 6 : 5);
            room -= size + (size == big ? 6 : 5);
        }
        if (2 * g.textWidth(label) <= room && faceHeight >= 2 * (g.fontHeight() - 1)) {
            g.pushPose();
            g.translate(textX, y + (faceHeight - 2 * (g.fontHeight() - 1)) / 2);
            g.scale(2, 2);
            g.drawText(label, 0, 0, color, true);
            g.popPose();
        } else {
            g.drawText(TitleScreenTheme.fit(g, label, room), textX, y + (faceHeight - g.fontHeight()) / 2 + 1, color, true);
        }
        int cx = x + w - 11 + Math.round(hover * 2), cy = y + faceHeight / 2;
        for (int i = 0; i < 4; i++) g.fill(cx + i, cy - 3 + i, cx + i + 1, cy + 4 - i, color);
    }

    private static void networkRow(LadsGraphics g, int x, int y, int w, int h, String label, String icon, boolean focused, boolean active, float hover) {
        g.fill(x, y, x + w, y + h, mix(PANEL, PANEL_ALT, hover));
        outline(g, x, y, w, h, focused && active ? FOCUS : hover > .5f ? LINE_ACCENT : LINE);
        int textY = y + (h - g.fontHeight()) / 2 + 1;
        String status = networkText();
        boolean up = !"offline".equals(status);
        String shown = 8 + g.textWidth(label) + 8 + 8 + g.textWidth(status) + 6 <= w ? status : up ? status.replace(" online", "") : "";
        int statusX = x + w - 6 - g.textWidth(shown);
        g.fill(statusX - 8, textY + 1, statusX - 3, textY + 6, up ? GREEN : DISABLED);
        g.drawText(shown, statusX, textY, up ? TEXT : MUTED, false);
        int lead = icon == null ? 0 : 13;
        if (icon != null) TitleScreenTheme.icon(g, icon, x + 8, textY, 9, active ? TEXT : DISABLED);
        g.drawText(TitleScreenTheme.fit(g, label, statusX - 10 - x - 8 - lead), x + 8 + lead, textY, active ? TEXT : DISABLED, false);
    }

    private static void link(LadsGraphics g, int x, int y, int w, int h, String label, String icon, boolean focused, boolean active, float hover) {
        if (hover > .01f || focused) {
            g.fill(x, y, x + w, y + h, 0x2ECC1427);
            g.fill(x, y, x + 2, y + h, focused && active ? FOCUS : RED);
        }
        int color = !active ? DISABLED : hover > .5f || focused ? TEXT : MUTED;
        int lead = icon == null ? 0 : 13; // the icons share one column, the labels start after it
        if (icon != null) TitleScreenTheme.icon(g, icon, x + 5, y + (h - 9) / 2, 9, color);
        g.drawText(TitleScreenTheme.fit(g, label, w - 5 - lead), x + 4 + lead, y + (h - g.fontHeight()) / 2 + 1, color, false);
    }

    /** During this layout's frame: a hard-edged compact button (header controls, Switch). False: no frame. */
    static boolean renderCompact(LadsGraphics g, int x, int y, int w, int h, String icon, String label, boolean focused, boolean active, float hover) {
        if (frame == null) return false;
        g.fill(x, y, x + w, y + h, mix(0xE6160D10, 0xF0301319, hover));
        outline(g, x, y, w, h, focused && active ? FOCUS : hover > .5f ? LINE_ACCENT : LINE);
        int color = active ? TEXT : DISABLED;
        if (label == null) {
            TitleScreenTheme.icon(g, icon, x + (w - 10) / 2, y + (h - 10) / 2, 10, color);
        } else {
            TitleScreenTheme.icon(g, icon, x + 6, y + (h - 10) / 2, 10, color);
            g.drawText(TitleScreenTheme.fit(g, label, w - TitleScreenTheme.COMPACT_PADDING), x + 20, y + (h - g.fontHeight()) / 2 + 1, color, false);
        }
        return true;
    }

    private static void outline(LadsGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static int mix(int a, int b, float t) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            result |= ((int) (((a >>> shift) & 255) * (1 - t) + ((b >>> shift) & 255) * t)) << shift;
        return result;
    }
}
