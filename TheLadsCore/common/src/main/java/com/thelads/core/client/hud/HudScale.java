package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

/**
 * The Lads HUD's own GUI scale (1.9.0): Auto (the default) follows vanilla's GUI scale; a fixed one is capped the way vanilla caps
 * its own at the window size. The Lads HUD lays out and draws in its own units: {@link #units} reports the screen in them, and the
 * caller scales the pose by 1 / hudUnit() around the drawing.
 */
public final class HudScale {
    private HudScale() {}

    /** The largest GUI scale a window of this many pixels allows (vanilla's rule: at least 320 x 240 GUI units). */
    public static int max(int pixelsWide, int pixelsHigh) {
        int scale = 1;
        while (scale < 16 && pixelsWide / (scale + 1) >= 320 && pixelsHigh / (scale + 1) >= 240) scale++;
        return scale;
    }

    /** The Lads HUD's scale on this graphics: the setting capped at the window, or vanilla's (Auto; 0 where vanilla's is unknown). */
    public static int of(LadsGraphics g) {
        int vanilla = g.guiScale(), chosen = HudSettings.getInstance().getHudScale();
        if (vanilla <= 0 || chosen <= 0) return vanilla;
        return Math.min(chosen, max(g.getScaledWidth() * vanilla, g.getScaledHeight() * vanilla));
    }

    /** The graphics in Lads HUD units, or itself when the two scales are the same. Drawing passes straight through. */
    public static LadsGraphics units(LadsGraphics g) {
        int vanilla = g.guiScale(), lads = of(g);
        return vanilla <= 0 || lads <= 0 || lads == vanilla ? g : new Units(g, vanilla / (double) lads, lads);
    }

    private static final class Units implements LadsGraphics {
        private final LadsGraphics target;
        private final double unit;
        private final int scale;

        Units(LadsGraphics target, double unit, int scale) {
            this.target = target;
            this.unit = unit;
            this.scale = scale;
        }

        @Override public int getScaledWidth() { return (int) (target.getScaledWidth() * unit); }
        @Override public int getScaledHeight() { return (int) (target.getScaledHeight() * unit); }
        @Override public int hotbarLift() { return (int) Math.round(target.hotbarLift() * unit); }
        @Override public int guiScale() { return scale; }
        @Override public double hudUnit() { return unit; }

        @Override public void fill(int minX, int minY, int maxX, int maxY, int color) { target.fill(minX, minY, maxX, maxY, color); }
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) { target.drawText(text, x, y, color, shadow); }
        @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) { target.drawCenteredText(text, centerX, y, color, shadow); }
        @Override public int textWidth(String text) { return target.textWidth(text); }
        @Override public Object textMetricsKey() { return target.textMetricsKey(); }
        @Override public int fontHeight() { return target.fontHeight(); }
        @Override public void pushPose() { target.pushPose(); }
        @Override public void popPose() { target.popPose(); }
        @Override public void translate(float x, float y) { target.translate(x, y); }
        @Override public void scale(float sx, float sy) { target.scale(sx, sy); }
        @Override public void enableScissor(int minX, int minY, int maxX, int maxY) { target.enableScissor(minX, minY, maxX, maxY); }
        @Override public void disableScissor() { target.disableScissor(); }
        @Override public void overlay(Runnable draw) { target.overlay(draw); }
        @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) { target.blit(texture, x, y, u, v, width, height); }
        @Override public void drawModIcon(String id, int x, int y, int size) { target.drawModIcon(id, x, y, size); }
        @Override public void drawHead(String username, String uuid, int x, int y, int size) { target.drawHead(username, uuid, x, y, size); }
        @Override public boolean drawKillBanner(String skin, int variant, int x, int y, int width, int height) { return target.drawKillBanner(skin, variant, x, y, width, height); }
        @Override public boolean drawKillBannerPreview(String skin, int variant, int x, int y, int width, int height, double clock) { return target.drawKillBannerPreview(skin, variant, x, y, width, height, clock); }
        @Override public void drawPlayerModel(int x, int y, int width, int height, boolean editor) { target.drawPlayerModel(x, y, width, height, editor); }
        @Override public void drawArmorItem(int index, int x, int y, boolean preview) { target.drawArmorItem(index, x, y, preview); }
        @Override public void drawHotbarSlots(int x, int y, int slots) { target.drawHotbarSlots(x, y, slots); }
        @Override public void drawArmorSlot(int slot, int x, int y, boolean preview) { target.drawArmorSlot(slot, x, y, preview); }
        @Override public void drawBossBars(int x, int y, int max, boolean names, boolean preview) { target.drawBossBars(x, y, max, names, preview); }
        @Override public void drawSprite(String sprite, int x, int y, int size) { target.drawSprite(sprite, x, y, size); }
        @Override public void drawPotionIcon(String effectId, int iconIndex, int x, int y, int size) { target.drawPotionIcon(effectId, iconIndex, x, y, size); }
        @Override public boolean drawGameView(int x, int y, int width, int height) { return target.drawGameView(x, y, width, height); }
        @Override public LadsGameBridge getGame() { return target.getGame(); }
    }
}
