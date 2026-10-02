package com.thelads.core.client.title;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.TitleScaleModule;
import java.util.ArrayList;
import java.util.List;

/** Shared, texture-free title artwork. Geometry is prepared once per resize. */
public final class TitleScreenTheme {
    private static final int TEXT = LadsPalette.TEXT;
    private static final int MUTED = LadsPalette.MUTED;
    private static final int ACCENT = LadsPalette.ACCENT;
    private TitleScreenTheme() {}

    public record Rect(int x, int y, int width, int height) {}
    public record Layout(int width, int height, boolean wide, int menuX, int menuY,
                         int menuWidth, List<Rect> buttons, int[] farRidge, int[] nearRidge) {}

    public static Layout layout(int width, int height, int buttonCount) { return layout(width, height, buttonCount, false); }

    /** {@code bottomRow}: Essential's row sits above the account card, so a narrow menu ends higher. */
    public static Layout layout(int width, int height, int buttonCount, boolean bottomRow) {
        width = Math.max(240, width);
        height = Math.max(180, height);
        buttonCount = Math.max(0, buttonCount);
        boolean wide = width >= 530 && height >= 280;
        int menuWidth = wide ? Math.min(248, width * 43 / 100) : Math.min(300, width - 32);
        int menuX = wide ? width - menuWidth - 28 : (width - menuWidth) / 2;
        int columns = buttonCount > 14 ? 3 : 2;
        int heroCount = Math.min(2, buttonCount);
        int gap = height < 220 ? 3 : height < 260 ? 5 : 6;
        int reserved = bottomRow && !wide ? ROW_SPACE : 0;
        int available = height - (wide ? 78 : height < 220 ? 71 : 104) - reserved;
        int smallRows = (Math.max(0, buttonCount - heroCount) + columns - 1) / columns;
        while (columns < 4 && heroCount * 18 + smallRows * 14 + Math.max(0, heroCount + smallRows - 1) * gap > available) {
            columns++;
            smallRows = (Math.max(0, buttonCount - heroCount) + columns - 1) / columns;
        }
        int smallHeight = Math.max(14, Math.min(27, (available - heroCount * 32 - gap * (heroCount + smallRows - 1)) / Math.max(1, smallRows)));
        int heroHeight = Math.max(18, Math.min(36, (available - smallRows * smallHeight - gap * (heroCount + smallRows - 1)) / Math.max(1, heroCount)));
        int menuHeight = heroCount * heroHeight + smallRows * smallHeight + Math.max(0, heroCount + smallRows - 1) * gap;
        int menuY = wide ? (height - menuHeight) / 2 + 4 : height < 220 ? 42 : Math.max(66, (height - reserved - menuHeight) / 2 + 14);
        List<Rect> buttons = new ArrayList<>(buttonCount);
        for (int i = 0; i < buttonCount; i++) {
            if (i < heroCount) buttons.add(new Rect(menuX, menuY + i * (heroHeight + gap), menuWidth, heroHeight));
            else {
                int slot = i - heroCount;
                int cellWidth = (menuWidth - (columns - 1) * gap) / columns;
                int rowCount = Math.min(columns, buttonCount - heroCount - (slot / columns) * columns);
                // The last odd action spans the row, keeping every native control reachable.
                int cellActualWidth = rowCount == 1 ? menuWidth : cellWidth;
                buttons.add(new Rect(menuX + (slot % columns) * (cellWidth + gap),
                    menuY + heroCount * (heroHeight + gap) + (slot / columns) * (smallHeight + gap), cellActualWidth, smallHeight));
            }
        }
        int samples = 96;
        int[] far = new int[samples + 1], near = new int[samples + 1];
        for (int i = 0; i <= samples; i++) {
            double x = (double) i / samples;
            far[i] = (int)(height * (.56 + .10 * Math.sin(x * 13 + .8) + .045 * Math.cos(x * 31)));
            near[i] = (int)(height * (.74 + .08 * Math.sin(x * 11 + 2.1) + .035 * Math.cos(x * 43)));
        }
        return new Layout(width, height, wide, menuX, menuY, menuWidth, List.copyOf(buttons), far, near);
    }

    public static void renderBackground(LadsGraphics g, Layout l, String username,
                                        String version, boolean online, double seconds) {
        int w = l.width(), h = l.height();
        for (int i = 0; i < 32; i++) {
            int top = i * h / 32, bottom = (i + 1) * h / 32;
            g.fill(0, top, w, bottom, mix(LadsPalette.BACKGROUND, 0xFF30111C, i / 31f));
        }
        // A restrained moving light ribbon: 20 translucent quads, no shaders or textures.
        int glowX = l.wide() ? w / 3 : w * 3 / 4;
        for (int i = 0; i < 20; i++) {
            int x = glowX - w / 4 + i * w / 38;
            int y = (int)(h * .22 + Math.sin(i * .18 + seconds * .12) * h * .045);
            g.fill(x, y, x + Math.max(2, w / 38), y + h / 4,
                alpha(0x00C73A48, 4 + (int)(10 * Math.sin(Math.PI * i / 20))));
        }
        for (int i = 0; i < 24; i++) {
            int x = (i * 137 + 31) % w;
            int y = 15 + (i * 47 + 9) % Math.max(20, h / 3);
            int strength = 60 + (int)(28 * (1 + Math.sin(seconds * .35 + i)));
            g.fill(x, y, x + 1, y + 1, alpha(0x00FFF1F1, strength));
        }
        int moonX = l.wide() ? w / 3 : w * 4 / 5, moonY = h / 5;
        disk(g, moonX, moonY, Math.max(13, h / 17), 0x14FF6666);
        disk(g, moonX, moonY, Math.max(10, h / 23), 0xFFEDC9C9);
        ridge(g, l.farRidge(), w, h, 0xFF200C15);
        ridge(g, l.nearRidge(), w, h, 0xFF10080C);
        for (int y = h * 4 / 5; y < h; y += Math.max(1, h / 30))
            g.fill(0, y, w, Math.min(h, y + Math.max(1, h / 30)), alpha(0x00070005, Math.min(160, (y - h * 4 / 5) * 400 / h)));

        int brandX = l.wide() ? 32 : l.menuX();
        int brandY = l.wide() ? Math.max(55, h / 2 - 70) : 16;
        if (l.wide()) {
            float brandScale = Math.min(TitleScaleModule.getScale(), Math.min(
                (l.menuX() - brandX - 28f) / Math.max(1, g.textWidth("LADS") * 4.6f),
                (h - 48f - brandY) / 125f));
            g.pushPose(); g.translate(brandX, brandY); g.scale(brandScale, brandScale); g.translate(-brandX, -brandY);
            g.fill(brandX, brandY - 18, brandX + 21, brandY - 16, ACCENT);
            g.drawText("BUILT FOR THE LADS", brandX + 29, brandY - 21, MUTED);
            scaledText(g, "THE", brandX, brandY, 2.4f, TEXT);
            scaledText(g, "LADS", brandX - 1, brandY + 27, 4.6f, TEXT);
            g.drawText("C L I E N T", brandX + 2, brandY + 76, ACCENT);
            g.drawText("Your worlds. Your people.", brandX + 2, brandY + 101, MUTED);
            g.drawText("Make yourself at home.", brandX + 2, brandY + 115, MUTED);
            g.popPose();
            int panelBottom = l.buttons().isEmpty() ? l.menuY() + 40 : l.buttons().get(l.buttons().size() - 1).y() + l.buttons().get(l.buttons().size() - 1).height();
            roundRect(g, l.menuX() - 12, l.menuY() - 30, l.menuWidth() + 24, panelBottom - l.menuY() + 42, 9, 0xCC130B10);
            g.drawText("LET'S PLAY", l.menuX(), l.menuY() - 18, MUTED);
            g.fill(l.menuX() + l.menuWidth() - 22, l.menuY() - 16, l.menuX() + l.menuWidth(), l.menuY() - 15, ACCENT);
        } else {
            mark(g, brandX, brandY, ACCENT);
            float brandScale = Math.min(2.05f * TitleScaleModule.getScale(), Math.min(
                (l.menuWidth() - 28f) / Math.max(1, g.textWidth("THE LADS")),
                (h < 220 ? 21f : 23f) / g.fontHeight()));
            scaledText(g, "THE LADS", brandX + 27, brandY - 1, brandScale, TEXT);
            if (h >= 220) g.drawText(fit(g, "C L I E N T  /  MAKE YOURSELF AT HOME", l.menuWidth() - 28), brandX + 28, brandY + 23, MUTED);
        }
        g.fill(16, h - 29, w - 16, h - 28, 0x2944202A);
        String build = com.thelads.core.LadsVersion.clientName() + " (" + version + ")";
        renderAccount(g, h, username, Math.min(l.wide() ? 170 : 150, w - g.textWidth(build) - 58));
        g.drawText(build, w - 18 - g.textWidth(build), h - 21, MUTED);
    }

    /** The signed-in account at the bottom left, as on the title screen (the pause menu shows it too). */
    public static void renderAccount(LadsGraphics g, int height, String username, int maxWidth) {
        var titleModule = ModuleManager.getInstance().getModule("TitleScreen");
        float accountScale = titleModule != null && titleModule.getOption("Account Card Scale") instanceof SliderOption value
            ? (float)value.getValue() / 100f : 1f;
        String name = fit(g, username == null ? "Player" : username, (int)(maxWidth / accountScale) - 15);
        g.pushPose(); g.translate(18, height - 21); g.scale(accountScale, accountScale);
        icon(g, "user", 0, 1, 9, ACCENT); g.drawText(name, 15, 0, TEXT); g.popPose();
    }

    /** Height a bottom row of Essential actions takes above the account card. */
    public static final int ROW_SPACE = 24;

    /**
     * Essential's actions in one row at the bottom left, just above the account card: labelled buttons, or square
     * icon buttons when the labels would not fit {@code maxWidth}.
     */
    public static List<Rect> essentialRow(int height, int maxWidth, int[] labelWidths) {
        int size = 18, gap = 4, total = gap * Math.max(0, labelWidths.length - 1);
        for (int width : labelWidths) total += width + COMPACT_PADDING;
        boolean compact = total > maxWidth;
        List<Rect> row = new ArrayList<>(labelWidths.length);
        int x = 16, y = height - 35 - size;
        for (int width : labelWidths) {
            int w = compact ? size : width + COMPACT_PADDING;
            row.add(new Rect(x, y, w, size));
            x += w + gap;
        }
        return row;
    }

    /** Room for Essential's row on the title screen: the whole width below the menu, or left of a menu that reaches down. */
    public static int essentialRowWidth(Layout l) {
        Rect last = l.buttons().isEmpty() ? null : l.buttons().get(l.buttons().size() - 1);
        boolean clear = !l.wide() || last == null || last.y() + last.height() + 16 <= l.height() - 35 - 18;
        return clear ? l.width() - 32 : l.menuX() - 40;
    }

    /** The icon for one of Essential's actions, by the label EssentialActions gives it. */
    public static String essentialIcon(String label) {
        return switch (label) {
            case "Social" -> "social";
            case "Wardrobe" -> "wardrobe";
            case "Pictures" -> "pictures";
            case "Host world" -> "host";
            default -> "essential";
        };
    }

    public static void renderButtonSurface(LadsGraphics g,int x,int y,int width,int height,boolean hovered,boolean focused,boolean active,float opacity) {
        renderButtonSurface(g,x,y,width,height,hovered,focused,active,opacity,0);
    }
    /** {@code lift}: the eased hover of a lifting button (ButtonLift), drawn as its glow. */
    public static void renderButtonSurface(LadsGraphics g,int x,int y,int width,int height,boolean hovered,boolean focused,boolean active,float opacity,float lift) {
        int a=Math.round(Math.max(0,Math.min(1,opacity))*255);
        glow(g,x,y,width,height,5,lift*opacity);
        if(focused&&active)roundRect(g,x-1,y-1,width+2,height+2,5,(ACCENT&0xFFFFFF)|a<<24);
        roundRect(g,x,y+2,width,height,5,(a/3)<<24);
        roundRect(g,x,y,width,height,5,((active?(hovered?LadsPalette.HOVER:LadsPalette.CARD):LadsPalette.PANEL)&0xFFFFFF)|a<<24);
        if(width>10)g.fill(x+5,y,x+width-5,y+1,(0x00FF6666)|((hovered?a/2:a/6)<<24));
    }
    public static void renderLogo(LadsGraphics g,int centerX,int top,int height) {
        float scale=Math.max(.5f,height/42f);
        g.pushPose();g.translate(centerX,top);g.scale(scale,scale);
        g.drawCenteredText("THE",0,0,LadsPalette.TEXT);
        g.pushPose();g.translate(0,10);g.scale(2,2);g.drawCenteredText("LADS",0,0,LadsPalette.TEXT);g.popPose();
        g.drawCenteredText("C L I E N T",0,32,LadsPalette.ACCENT);g.popPose();
    }
    public static void renderButton(LadsGraphics g, int x, int y, int width, int height,
                                     String label, String icon, boolean primary, boolean hovered,
                                     boolean focused, boolean active, float hoverProgress) {
        float hover = active ? Math.max(0, Math.min(1, hoverProgress)) : 0;
        float lift = hover * hover * (3 - 2 * hover);
        // Hover: the button grows a little about its centre and glows.
        g.pushPose();
        g.translate(x + width / 2f, y + height / 2f);
        g.scale(1 + LIFT * lift, 1 + LIFT * lift);
        g.translate(-(x + width / 2f), -(y + height / 2f));
        glow(g, x, y, width, height, 5, lift);
        if (focused && active) roundRect(g, x - 2, y - 2, width + 4, height + 4, 7, ACCENT);
        int base = primary ? LadsPalette.PRIMARY : LadsPalette.CARD;
        int target = primary ? LadsPalette.PRIMARY_HOVER : LadsPalette.HOVER;
        roundRect(g, x, y + 2, width, height, 5, 0x51060003);
        roundRect(g, x, y, width, height, 5, active ? mix(base, target, hover) : LadsPalette.PANEL);
        g.fill(x + 5, y, x + width - 5, y + 1, primary ? 0x66FF6666 : alpha(0x00FF6666, active ? 28 + (int)(hover * 62) : 12));
        int color = !active ? LadsPalette.DISABLED : TEXT;
        int iconSize = height < 23 ? 8 : 10;
        int inset = width < 100 ? 8 : 12;
        icon(g, icon, x + inset, y + (height - iconSize) / 2, iconSize, color);
        String caption = fit(g, label, width - inset - iconSize - 24);
        g.drawText(caption, x + inset + iconSize + 8, y + (height - g.fontHeight()) / 2 + 1, color);
        if (width >= 150) {
            int arrowX = x + width - 15 + (int)(hover * 2), arrowY = y + height / 2;
            g.fill(arrowX - 3, arrowY - 2, arrowX - 2, arrowY + 3, color);
            g.fill(arrowX - 2, arrowY - 1, arrowX - 1, arrowY + 2, color);
            g.fill(arrowX - 1, arrowY, arrowX, arrowY + 1, color);
        }
        g.popPose();
    }

    /** How much bigger a fully hovered button draws. */
    public static final float LIFT = .04f;

    /** A soft red halo around a hovered button, breathing slowly while the pointer stays. */
    public static void glow(LadsGraphics g, int x, int y, int width, int height, int radius, float lift) {
        if (lift <= .01f) return;
        float breathe = .82f + .18f * (float) Math.sin(System.nanoTime() / 1e9 * 4.2);
        int[] alphas = {70, 42, 22, 10};
        for (int i = alphas.length; i >= 1; i--)
            roundRect(g, x - i, y - i, width + 2 * i, height + 2 * i, radius + i, alpha(0x00E0303A, Math.round(alphas[i - 1] * lift * breathe)));
    }

    /**
     * A compact button with the same hover lift and glow: an icon, followed by its label when one is given (the Essential
     * row), or the icon alone, centred (the pause menu's fullscreen toggle).
     */
    public static void renderCompactButton(LadsGraphics g, int x, int y, int width, int height, String icon, String label,
                                           boolean focused, boolean active, float hoverProgress) {
        float hover = active ? Math.max(0, Math.min(1, hoverProgress)) : 0;
        float lift = hover * hover * (3 - 2 * hover), scale = 1 + 2 * LIFT * lift;
        g.pushPose();
        g.translate(x + width / 2f, y + height / 2f);
        g.scale(scale, scale);
        g.translate(-(x + width / 2f), -(y + height / 2f));
        glow(g, x, y, width, height, 5, lift);
        if (focused && active) roundRect(g, x - 2, y - 2, width + 4, height + 4, 7, ACCENT);
        roundRect(g, x, y + 2, width, height, 5, 0x51060003);
        roundRect(g, x, y, width, height, 5, active ? mix(LadsPalette.CARD, LadsPalette.HOVER, hover) : LadsPalette.PANEL);
        int color = active ? TEXT : LadsPalette.DISABLED;
        if (label == null) icon(g, icon, x + (width - 10) / 2, y + (height - 10) / 2, 10, color);
        else {
            icon(g, icon, x + 6, y + (height - 10) / 2, 10, color);
            g.drawText(fit(g, label, width - COMPACT_PADDING), x + 20, y + (height - g.fontHeight()) / 2 + 1, color);
        }
        g.popPose();
    }

    /** A compact button's width beyond its label: icon, gaps and edges. */
    public static final int COMPACT_PADDING = 26;

    private static void ridge(LadsGraphics g, int[] heights, int width, int bottom, int color) {
        // Bound work even at 4K or GUI scale 1; each layer uses at most 240 strips.
        int strips = Math.min(240, Math.max(1, width / 2));
        for (int strip = 0; strip < strips; strip++) {
            int x = strip * width / strips, end = (strip + 1) * width / strips;
            float sample = (float) strip * (heights.length - 1) / strips;
            int i = (int) sample;
            int top = (int)(heights[i] + (heights[i + 1] - heights[i]) * (sample - i));
            g.fill(x, top, end, bottom, color);
        }
    }

    private static void scaledText(LadsGraphics g, String text, int x, int y, float scale, int color) {
        g.pushPose();
        g.translate(x, y);
        g.scale(scale, scale);
        g.drawText(text, 0, 0, color);
        g.popPose();
    }

    private static void mark(LadsGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 5, y + 20, color);
        g.fill(x, y + 15, x + 18, y + 20, color);
        g.fill(x + 10, y, x + 15, y + 11, color);
        g.fill(x + 10, y + 6, x + 22, y + 11, color);
    }

    private static void icon(LadsGraphics g, String kind, int x, int y, int size, int c) {
        switch (kind) {
            case "play" -> {
                for (int i = 0; i < size / 2; i++) g.fill(x + i, y + i, x + i + 1, y + size - i, c);
            }
            case "user" -> {
                g.fill(x + 3, y, x + size - 3, y + 4, c);
                g.fill(x + 1, y + 6, x + size - 1, y + size, c);
            }
            case "server", "realms" -> {
                for (int i = 0; i < 3; i++) {
                    g.fill(x, y + i * 4, x + size, y + i * 4 + 2, c);
                    g.fill(x + size - 2, y + i * 4, x + size - 1, y + i * 4 + 1, LadsPalette.PRIMARY_PRESSED);
                }
            }
            case "quit" -> {
                g.fill(x + 4, y, x + 6, y + 6, c);
                g.fill(x + 1, y + 3, x + 2, y + 8, c);
                g.fill(x + 8, y + 3, x + 9, y + 8, c);
                g.fill(x + 2, y + 8, x + 8, y + 10, c);
            }
            case "access" -> {
                g.fill(x + 4, y, x + 6, y + 2, c);
                g.fill(x, y + 3, x + 10, y + 4, c);
                g.fill(x + 4, y + 4, x + 6, y + 7, c);
                g.fill(x + 2, y + 7, x + 4, y + 10, c);
                g.fill(x + 6, y + 7, x + 8, y + 10, c);
            }
            case "language" -> {
                g.fill(x, y + 1, x + size, y + 2, c);
                g.fill(x + 4, y, x + 5, y + size, c);
                g.fill(x + 1, y + size - 2, x + size - 1, y + size - 1, c);
                g.fill(x + 1, y + 3, x + 2, y + 7, c);
                g.fill(x + size - 2, y + 3, x + size - 1, y + 7, c);
            }
            case "fullscreen", "windowed" -> {
                // The usual fullscreen glyph: four corner brackets, opening outward (enter) or inward (leave).
                int t = Math.max(1, size / 8), arm = Math.max(3, size * 3 / 8);
                boolean in = kind.equals("windowed");
                for (int corner = 0; corner < 4; corner++) {
                    boolean right = corner % 2 == 1, bottom = corner >= 2;
                    int cx = right ? x + size - t : x, cy = bottom ? y + size - t : y;
                    int dx = right ? -1 : 1, dy = bottom ? -1 : 1;
                    if (in) { cx += dx * (arm - t); cy += dy * (arm - t); dx = -dx; dy = -dy; }
                    int ax = dx > 0 ? cx : cx + t - arm, ay = dy > 0 ? cy : cy + t - arm;
                    g.fill(ax, cy, ax + arm, cy + t, c);
                    g.fill(cx, ay, cx + t, ay + arm, c);
                }
            }
            case "social" -> {
                g.fill(x + 1, y + 1, x + 4, y + 4, c);
                g.fill(x, y + 5, x + 5, y + 9, c);
                g.fill(x + 6, y, x + 9, y + 3, c);
                g.fill(x + 5, y + 4, x + 10, y + 8, c);
            }
            case "wardrobe" -> {
                g.fill(x, y + 1, x + 10, y + 4, c);
                g.fill(x + 2, y + 4, x + 8, y + 10, c);
            }
            case "pictures" -> {
                g.fill(x, y + 1, x + 10, y + 2, c);
                g.fill(x, y + 8, x + 10, y + 9, c);
                g.fill(x, y + 2, x + 1, y + 8, c);
                g.fill(x + 9, y + 2, x + 10, y + 8, c);
                g.fill(x + 2, y + 6, x + 5, y + 8, c);
                g.fill(x + 4, y + 4, x + 8, y + 8, c);
            }
            case "host" -> {
                for (int i = 0; i < 5; i++) g.fill(x + 4 - i, y + i, x + 6 + i, y + i + 1, c);
                g.fill(x + 1, y + 5, x + 9, y + 10, c);
            }
            case "essential" -> {
                g.fill(x + 1, y, x + 3, y + 10, c);
                g.fill(x + 3, y, x + 9, y + 2, c);
                g.fill(x + 3, y + 4, x + 8, y + 6, c);
                g.fill(x + 3, y + 8, x + 9, y + 10, c);
            }
            default -> {
                for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++)
                    g.fill(x + col * 6, y + row * 6, x + col * 6 + 4, y + row * 6 + 4, c);
            }
        }
    }

    private static void disk(LadsGraphics g, int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int half = (int)Math.sqrt(radius * radius - y * y);
            g.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, color);
        }
    }

    private static void roundRect(LadsGraphics g, int x, int y, int w, int h, int radius, int color) {
        int r = Math.min(radius, Math.min(w, h) / 2);
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            int inset = r - (int)Math.sqrt(r * r - (r - row - 1) * (r - row - 1));
            g.fill(x + inset, y + row, x + r, y + row + 1, color);
            g.fill(x + w - r, y + row, x + w - inset, y + row + 1, color);
            g.fill(x + inset, y + h - row - 1, x + r, y + h - row, color);
            g.fill(x + w - r, y + h - row - 1, x + w - inset, y + h - row, color);
        }
    }

    private static String fit(LadsGraphics g, String value, int width) {
        if (value == null) return "";
        if (g.textWidth(value) <= width) return value;
        int end = value.length();
        while (end > 0 && g.textWidth(value.substring(0, end) + "...") > width) end--;
        return end == 0 ? "" : value.substring(0, end) + "...";
    }

    private static int alpha(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }
    private static int mix(int a, int b, float t) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            result |= ((int)(((a >>> shift) & 255) * (1 - t) + ((b >>> shift) & 255) * t)) << shift;
        return result;
    }
}
