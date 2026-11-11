package com.thelads.core.client.social.ui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.gui.MenuGraphics;
import com.thelads.core.client.social.DmMedia;
import com.thelads.core.client.social.ui.FriendsScreenLogic.Box;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The DM pane's "send a screenshot" list: the game's screenshots folder only (no file picker), newest first, with a preview
 * of the one under the mouse. Clicking one picks it; Esc, Close or a click outside closes the list.
 */
public final class DmScreenshotPicker {
    private static final int ROW_H = 14;
    private final Supplier<Path> gameDir;
    private List<Path> shots = List.of();
    private boolean open;
    private int scroll, visibleRows;
    private Box panel, closeBtn;
    private final List<Box> rows = new ArrayList<>();
    private Path previewed;

    public DmScreenshotPicker(Supplier<Path> gameDir) {
        this.gameDir = gameDir;
    }

    public void open() {
        shots = DmMedia.screenshots(gameDir.get());
        scroll = 0;
        previewed = shots.isEmpty() ? null : shots.get(0);
        open = true;
    }

    public void close() { open = false; }
    public boolean isOpen() { return open; }
    public Path gameDir() { return gameDir.get(); }

    public void render(LadsGraphics g, int x, int y, int w, int h, int mx, int my) {
        if (!open) return;
        rows.clear();
        panel = new Box(x, y, w, h);
        g.overlay(() -> {
            MenuGraphics.round(g, x, y, w, h, LadsPalette.PANEL);
            g.fill(x, y, x + w, y + 1, LadsPalette.ACCENT);
            g.drawText("Send a screenshot", x + 8, y + 7, LadsPalette.TEXT, true);
            closeBtn = new Box(x + w - 56, y + 4, 50, 16);
            MenuGraphics.round(g, closeBtn.x(), closeBtn.y(), closeBtn.w(), closeBtn.h(), closeBtn.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD);
            MenuGraphics.iconLabel(g, "Close", null, closeBtn.x() + closeBtn.w() / 2, closeBtn.y() + 4, closeBtn.w(), LadsPalette.TEXT, false);
            if (shots.isEmpty()) {
                g.drawCenteredText("No screenshots yet: press F2 in game to take one.", x + w / 2, y + h / 2 - 4, LadsPalette.MUTED, false);
                return;
            }
            int listX = x + 6, listY = y + 24, listW = Math.max(60, w * 2 / 5), listH = h - 30;
            visibleRows = Math.max(1, listH / ROW_H);
            scroll = Math.max(0, Math.min(scroll, shots.size() - visibleRows));
            for (int i = 0; i < visibleRows && scroll + i < shots.size(); i++) {
                Path shot = shots.get(scroll + i);
                Box row = new Box(listX, listY + i * ROW_H, listW, ROW_H - 1);
                rows.add(row);
                boolean hov = row.has(mx, my);
                if (hov) previewed = shot;
                MenuGraphics.round(g, row.x(), row.y(), row.w(), row.h(), hov ? LadsPalette.HOVER : shot.equals(previewed) ? LadsPalette.CARD : LadsPalette.PANEL);
                g.drawText(MenuGraphics.fit(g, shot.getFileName().toString(), listW - 8), row.x() + 4, row.y() + 3, LadsPalette.TEXT, false);
            }
            int px = listX + listW + 8, pw = x + w - 8 - px, ph = h - 46;
            DmMedia.Media thumb = DmMedia.thumbnail(previewed);
            int[] size = DmMedia.fit(thumb, pw, ph);
            int ix = px + (pw - size[0]) / 2, iy = y + 24;
            g.fill(ix, iy, ix + size[0], iy + size[1], LadsPalette.CARD);
            if (!DmMedia.draw(g, thumb, ix, iy, size[0], size[1], 0)) {
                String state = thumb != null && thumb.failed() ? "Can't read this picture" : "Loading...";
                g.drawCenteredText(state, ix + size[0] / 2, iy + size[1] / 2 - 4, LadsPalette.MUTED, false);
            }
            g.drawCenteredText("Click a screenshot to send it", px + pw / 2, y + h - 16, LadsPalette.MUTED, false);
        });
    }

    /** The screenshot clicked (the list closes), or null; any click while open is taken. */
    public Path click(double mx, double my) {
        if (!open) return null;
        if (panel == null || !panel.has(mx, my) || closeBtn != null && closeBtn.has(mx, my)) {
            open = false;
            return null;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).has(mx, my) && scroll + i < shots.size()) {
                open = false;
                return shots.get(scroll + i);
            }
        }
        return null;
    }

    public boolean scroll(double amount) {
        if (!open) return false;
        scroll = Math.max(0, Math.min(Math.max(0, shots.size() - visibleRows), scroll - (int) Math.signum(amount) * 3));
        return true;
    }
}
