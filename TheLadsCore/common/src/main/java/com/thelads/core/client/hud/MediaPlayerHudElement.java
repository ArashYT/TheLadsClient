package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

/**
 * Lunar & Badlion parity Media Player overlay.
 * Displays title, artist, playback status, and progress overlay.
 * Visual-only.
 */
public class MediaPlayerHudElement extends HudElement {
    public static final String MODULE_NAME = "MediaPlayer";

    public static class MediaTrack {
        public String title = "Never Gonna Give You Up";
        public String artist = "Rick Astley";
        public boolean isPlaying = true;
        public int positionSeconds = 83;
        public int durationSeconds = 212;
    }

    private static MediaTrack currentTrack = new MediaTrack();

    public static void updateTrack(String title, String artist, boolean playing, int positionSec, int durationSec) {
        currentTrack.title = title != null ? title : "Unknown Title";
        currentTrack.artist = artist != null ? artist : "Unknown Artist";
        currentTrack.isPlaying = playing;
        currentTrack.positionSeconds = positionSec;
        currentTrack.durationSeconds = durationSec;
    }

    public MediaPlayerHudElement() {
        useOrganizedDefaults();
    }

    @Override
    public void prepareRender(LadsGraphics graphics, boolean editor) {
        width = 130;
        height = 26;
    }

    @Override
    public void render(LadsGraphics graphics) {
        if (graphics == null) return;
        drawBackground(graphics);

        boolean shadow = HudSettings.getInstance().isTextShadow();
        int textCol = resolveColor();
        String title = currentTrack.title;
        if (graphics.textWidth(title) > width - 20) {
            title = title.substring(0, Math.min(title.length(), 14)) + "..";
        }

        String playIcon = currentTrack.isPlaying ? "▶ " : "❚❚ ";
        graphics.drawText(playIcon, x + 4, y + 4, 0xFF55FF55, shadow);
        int iconW = graphics.textWidth(playIcon);

        graphics.drawText(title, x + 4 + iconW, y + 4, textCol, shadow);

        // Progress bar and time
        int barX = x + 4, barY = y + 17, barW = width - 8, barH = 3;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF444444);

        float progress = currentTrack.durationSeconds > 0
            ? Math.min(1.0f, (float) currentTrack.positionSeconds / currentTrack.durationSeconds)
            : 0f;
        int fillW = (int) (barW * progress);
        if (fillW > 0) {
            graphics.fill(barX, barY, barX + fillW, barY + barH, 0xFF1DB954); // Spotify green accent
        }

        String timeStr = formatTime(currentTrack.positionSeconds) + "/" + formatTime(currentTrack.durationSeconds);
        int tw = graphics.textWidth(timeStr);
        if (tw < width - 10) {
            graphics.drawText(timeStr, x + width - tw - 4, y + 4, 0xFFAAAAAA, shadow);
        }
    }

    private static String formatTime(int totalSec) {
        int m = totalSec / 60;
        int s = totalSec % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", m, s);
    }
}
