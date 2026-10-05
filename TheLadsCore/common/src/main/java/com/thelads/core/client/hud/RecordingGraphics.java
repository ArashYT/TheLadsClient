package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import java.util.List;
import java.util.function.Consumer;

/**
 * Records draw calls for replay on later frames; measurements come from the real graphics. Used by the HUD FPS cap: the HUD is
 * rebuilt at the cap rate and the last build is drawn on every frame in between, so it stays on screen instead of blinking.
 */
final class RecordingGraphics implements LadsGraphics {
    private final LadsGraphics target;
    private final List<Consumer<LadsGraphics>> ops;

    RecordingGraphics(LadsGraphics target, List<Consumer<LadsGraphics>> ops) {
        this.target = target;
        this.ops = ops;
    }

    @Override public void fill(int minX, int minY, int maxX, int maxY, int color) { ops.add(g -> g.fill(minX, minY, maxX, maxY, color)); }
    @Override public void drawText(String text, int x, int y, int color, boolean shadow) { ops.add(g -> g.drawText(text, x, y, color, shadow)); }
    @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) { ops.add(g -> g.drawCenteredText(text, centerX, y, color, shadow)); }
    @Override public void pushPose() { ops.add(LadsGraphics::pushPose); }
    @Override public void popPose() { ops.add(LadsGraphics::popPose); }
    @Override public void translate(float x, float y) { ops.add(g -> g.translate(x, y)); }
    @Override public void scale(float sx, float sy) { ops.add(g -> g.scale(sx, sy)); }
    @Override public void enableScissor(int minX, int minY, int maxX, int maxY) { ops.add(g -> g.enableScissor(minX, minY, maxX, maxY)); }
    @Override public void disableScissor() { ops.add(LadsGraphics::disableScissor); }
    @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) { ops.add(g -> g.blit(texture, x, y, u, v, width, height)); }
    @Override public void drawModIcon(String id, int x, int y, int size) { ops.add(g -> g.drawModIcon(id, x, y, size)); }
    @Override public void drawHead(String username, String uuid, int x, int y, int size) { ops.add(g -> g.drawHead(username, uuid, x, y, size)); }
    @Override public void drawPlayerModel(int x, int y, int width, int height, boolean editor) { ops.add(g -> g.drawPlayerModel(x, y, width, height, editor)); }
    @Override public void drawArmorItem(int index, int x, int y, boolean preview) { ops.add(g -> g.drawArmorItem(index, x, y, preview)); }
    @Override public void drawHotbarSlots(int x, int y, int slots) { ops.add(g -> g.drawHotbarSlots(x, y, slots)); }
    @Override public void drawArmorSlot(int slot, int x, int y, boolean preview) { ops.add(g -> g.drawArmorSlot(slot, x, y, preview)); }
    @Override public void drawSprite(String sprite, int x, int y, int size) { ops.add(g -> g.drawSprite(sprite, x, y, size)); }
    @Override public void drawBossBars(int x, int y, int max, boolean names, boolean preview) { ops.add(g -> g.drawBossBars(x, y, max, names, preview)); }
    // Screen-only art (the Kill Banner picker, the HUD editor backdrop): recorded like the rest, assumed drawn.
    @Override public boolean drawKillBanner(String skin, int variant, int x, int y, int width, int height) { ops.add(g -> g.drawKillBanner(skin, variant, x, y, width, height)); return true; }
    @Override public boolean drawGameView(int x, int y, int width, int height) { ops.add(g -> g.drawGameView(x, y, width, height)); return true; }

    @Override public int textWidth(String text) { return target.textWidth(text); }
    @Override public Object textMetricsKey() { return target.textMetricsKey(); }
    @Override public int fontHeight() { return target.fontHeight(); }
    @Override public int getScaledWidth() { return target.getScaledWidth(); }
    @Override public int getScaledHeight() { return target.getScaledHeight(); }
    @Override public int hotbarLift() { return target.hotbarLift(); }
    @Override public LadsGameBridge getGame() { return target.getGame(); }
}
