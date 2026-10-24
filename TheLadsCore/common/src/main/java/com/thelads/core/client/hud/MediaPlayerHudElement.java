package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

/** Media Player: what Windows plays now (MediaSession): title, artist and the track's progress. Nothing playing shows nothing. */
public class MediaPlayerHudElement extends HudElement {
    private static final int MAX_WIDTH = 180;
    private boolean editor, shown, artist, bar, time;
    private String title = "", by = "", clock = "";
    private String sourceTitle, sourceArtist;
    private long clockSecond = -1, clockLength = -1;
    private float progress;

    public MediaPlayerHudElement() {
        width = 150;
        height = 34;
    }

    @Override
    public boolean isAvailable() {
        return MediaSession.SUPPORTED;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        this.editor = editor;
        String name = MediaSession.title, who = MediaSession.artist;
        shown = !name.isEmpty() && (MediaSession.playing || !optBool("Hide When Paused", false));
        int progressMode = optCycle("Progress", 3); // Off, Bar, Time, Both
        artist = optBool("Show Artist", true);
        bar = progressMode == 1 || progressMode == 3;
        time = progressMode >= 2;
        long length = shown ? MediaSession.durationMs : 212_000, at = shown ? MediaSession.position(System.currentTimeMillis()) : 83_000;
        if (!shown) {
            name = "Song title";
            who = "Artist";
        }
        if (!name.equals(sourceTitle) || !who.equals(sourceArtist)) { // trimmed once per track, not per frame
            sourceTitle = name;
            sourceArtist = who;
            title = fit(g, name);
            by = fit(g, who);
        }
        if (at / 1000 != clockSecond || length != clockLength) {
            clockSecond = at / 1000;
            clockLength = length;
            clock = length > 0 ? clock(at) + " / " + clock(length) : clock(at);
        }
        progress = length > 0 ? Math.min(1, at / (float) length) : 0;
        width = Math.max(100, Math.max(g.textWidth(title), artist ? g.textWidth(by) : 0) + 10);
        height = 4 + 10 + (artist ? 10 : 0) + (time ? 10 : 0) + (bar ? 5 : 0) + 2;
    }

    private static String fit(LadsGraphics g, String text) {
        if (g.textWidth(text) <= MAX_WIDTH) return text;
        int end = text.length();
        while (end > 1 && g.textWidth(text.substring(0, end) + "...") > MAX_WIDTH) end--;
        return text.substring(0, end) + "...";
    }

    /** m:ss */
    static String clock(long millis) {
        long seconds = millis / 1000;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    @Override
    public void render(LadsGraphics g) {
        if (!shown && !editor) return;
        drawBackground(g);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        int line = y + 4;
        g.drawText(title, x + 5, line, resolveColor(), shadow);
        line += 10;
        if (artist) {
            g.drawText(by, x + 5, line, 0xFFAAAAAA, shadow);
            line += 10;
        }
        if (time) {
            g.drawText(clock, x + 5, line, 0xFFAAAAAA, shadow);
            line += 10;
        }
        if (bar) {
            g.fill(x + 5, line, x + width - 5, line + 3, 0xFF404040);
            g.fill(x + 5, line, x + 5 + (int) ((width - 10) * progress), line + 3, resolveColor());
        }
    }
}
