package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

    private static int hash(int a, int b) { return 31 * (31 + a) + b; }
    private static int hash(int a, int b, int c) { return 31 * (31 * (31 + a) + b) + c; }
    private static int hash(int a, int b, int c, int d) { return 31 * (31 * (31 * (31 + a) + b) + c) + d; }
    private static int hash(int a, int b, int c, int d, int e) { return 31 * (31 * (31 * (31 * (31 + a) + b) + c) + d) + e; }
    private static int hash(int a, int b, int c, int d, int e, int f) { return 31 * (31 * (31 * (31 * (31 * (31 + a) + b) + c) + d) + e) + f; }

    private static void mark0(String kind) { HudFrameCap.draw(31 * kind.hashCode(), 1); }
    private static void mark(String kind, int what, int a, int b) { HudFrameCap.draw(31 * kind.hashCode() + what, hash(a, b)); }
    private static void mark(String kind, int what, int a, int b, int c) { HudFrameCap.draw(31 * kind.hashCode() + what, hash(a, b, c)); }
    private static void mark(String kind, int what, int a, int b, int c, int d) { HudFrameCap.draw(31 * kind.hashCode() + what, hash(a, b, c, d)); }
    private static void mark(String kind, int what, int a, int b, int c, int d, int e) { HudFrameCap.draw(31 * kind.hashCode() + what, hash(a, b, c, d, e)); }

    @Override public void fill(int minX, int minY, int maxX, int maxY, int color) { mark("fill", 0, minX, minY, maxX, maxY, color); ops.add(g -> g.fill(minX, minY, maxX, maxY, color)); }
    @Override public void drawText(String text, int x, int y, int color, boolean shadow) { mark("text", Objects.hashCode(text), x, y, color, shadow ? 1231 : 1237); ops.add(g -> g.drawText(text, x, y, color, shadow)); }
    @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) { mark("centered", Objects.hashCode(text), centerX, y, color, shadow ? 1231 : 1237); ops.add(g -> g.drawCenteredText(text, centerX, y, color, shadow)); }
    @Override public void pushPose() { mark0("push"); ops.add(LadsGraphics::pushPose); }
    @Override public void popPose() { mark0("pop"); ops.add(LadsGraphics::popPose); }
    @Override public void translate(float x, float y) { mark("translate", 0, Float.floatToIntBits(x), Float.floatToIntBits(y)); ops.add(g -> g.translate(x, y)); }
    @Override public void scale(float sx, float sy) { mark("scale", 0, Float.floatToIntBits(sx), Float.floatToIntBits(sy)); ops.add(g -> g.scale(sx, sy)); }
    @Override public void enableScissor(int minX, int minY, int maxX, int maxY) { mark("scissor", 0, minX, minY, maxX, maxY); ops.add(g -> g.enableScissor(minX, minY, maxX, maxY)); }
    @Override public void disableScissor() { mark0("unscissor"); ops.add(LadsGraphics::disableScissor); }
    // Menus and dialogs draw through their own graphics, not a recorded HUD build: nothing to replay, the layer just runs.
    @Override public void overlay(Runnable draw) { draw.run(); }
    @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) { mark("blit", hash(Objects.hashCode(texture), u, v), x, y, width, height); ops.add(g -> g.blit(texture, x, y, u, v, width, height)); }
    @Override public void drawModIcon(String id, int x, int y, int size) { mark("icon", Objects.hashCode(id), x, y, size); ops.add(g -> g.drawModIcon(id, x, y, size)); }
    // These draw the game's current state each time they are replayed (the player, armour, boss bars, heads), so they are live anyway.
    @Override public void drawHead(String username, String uuid, int x, int y, int size) { mark("head", hash(Objects.hashCode(username), Objects.hashCode(uuid)), x, y, size); ops.add(g -> g.drawHead(username, uuid, x, y, size)); }
    @Override public void drawPlayerModel(int x, int y, int width, int height, boolean editor) { mark("model", editor ? 1231 : 1237, x, y, width, height); ops.add(g -> g.drawPlayerModel(x, y, width, height, editor)); }
    @Override public void drawArmorItem(int index, int x, int y, boolean preview) { mark("armor", hash(index, preview ? 1231 : 1237), x, y); ops.add(g -> g.drawArmorItem(index, x, y, preview)); }
    @Override public void drawHotbarSlots(int x, int y, int slots) { mark("slots", slots, x, y); ops.add(g -> g.drawHotbarSlots(x, y, slots)); }
    @Override public void drawArmorSlot(int slot, int x, int y, boolean preview) { mark("armorSlot", hash(slot, preview ? 1231 : 1237), x, y); ops.add(g -> g.drawArmorSlot(slot, x, y, preview)); }
    @Override public void drawSprite(String sprite, int x, int y, int size) { mark("sprite", Objects.hashCode(sprite), x, y, size); ops.add(g -> g.drawSprite(sprite, x, y, size)); }
    @Override public void drawPotionIcon(String effectId, int iconIndex, int x, int y, int size) { mark("potionIcon", hash(Objects.hashCode(effectId), iconIndex), x, y, size); ops.add(g -> g.drawPotionIcon(effectId, iconIndex, x, y, size)); }
    @Override public void drawPackIcon(String pack, int x, int y, int size) { mark("packIcon", Objects.hashCode(pack), x, y, size); ops.add(g -> g.drawPackIcon(pack, x, y, size)); }
    @Override public void drawBossBars(int x, int y, int max, boolean names, boolean preview) { mark("bossBars", hash(max, names ? 1231 : 1237, preview ? 1231 : 1237), x, y); ops.add(g -> g.drawBossBars(x, y, max, names, preview)); }
    @Override public void drawActionBar(Object text, int x, int y, int color, boolean shadow, boolean plain) { mark("actionBar", Objects.hashCode(text), x, y, color, hash(shadow ? 1231 : 1237, plain ? 1231 : 1237)); ops.add(g -> g.drawActionBar(text, x, y, color, shadow, plain)); }
    @Override public int actionBarWidth(Object text) { return target.actionBarWidth(text); }
    // Screen-only art (the Kill Banner picker, the HUD editor backdrop): recorded like the rest, assumed drawn.
    @Override public boolean drawKillBanner(String skin, int variant, int x, int y, int width, int height) { mark("banner", hash(Objects.hashCode(skin), variant), x, y, width, height); ops.add(g -> g.drawKillBanner(skin, variant, x, y, width, height)); return true; }
    @Override public boolean drawKillBannerPreview(String skin, int variant, int x, int y, int width, int height, double clock) { long cb = Double.doubleToLongBits(clock); mark("bannerPreview", hash(Objects.hashCode(skin), variant), x, y, width, hash(height, (int)(cb ^ (cb >>> 32)))); ops.add(g -> g.drawKillBannerPreview(skin, variant, x, y, width, height, clock)); return true; }
    @Override public boolean drawCrosshairPreview(int cx, int cy) { mark("crosshair", 0, cx, cy); ops.add(g -> g.drawCrosshairPreview(cx, cy)); return true; }
    @Override public boolean drawGameView(int x, int y, int width, int height) { mark("view", 0, x, y, width, height); ops.add(g -> g.drawGameView(x, y, width, height)); return true; }
    @Override public boolean drawImage(String source, int x, int y, int width, int height) { mark("image", Objects.hashCode(source), x, y, width, height); ops.add(g -> g.drawImage(source, x, y, width, height)); return true; }

    @Override public int textWidth(String text) { return target.textWidth(text); }
    @Override public Object textMetricsKey() { return target.textMetricsKey(); }
    @Override public int fontHeight() { return target.fontHeight(); }
    @Override public int getScaledWidth() { return target.getScaledWidth(); }
    @Override public int getScaledHeight() { return target.getScaledHeight(); }
    @Override public int hotbarLift() { return target.hotbarLift(); }
    @Override public int guiScale() { return target.guiScale(); }
    @Override public double hudUnit() { return target.hudUnit(); }
    @Override public LadsGameBridge getGame() { return target.getGame(); }
}
