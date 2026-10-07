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

    /** For HudFrameCap: what each call draws, and where and how (moving, resizing, fading, recolouring are animation; new content is not). */
    private static void mark(String kind, Object what, Object... how) { HudFrameCap.draw(31 * kind.hashCode() + Objects.hashCode(what), Arrays.hashCode(how)); }

    @Override public void fill(int minX, int minY, int maxX, int maxY, int color) { mark("fill", null, minX, minY, maxX, maxY, color); ops.add(g -> g.fill(minX, minY, maxX, maxY, color)); }
    @Override public void drawText(String text, int x, int y, int color, boolean shadow) { mark("text", text, x, y, color, shadow); ops.add(g -> g.drawText(text, x, y, color, shadow)); }
    @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) { mark("centered", text, centerX, y, color, shadow); ops.add(g -> g.drawCenteredText(text, centerX, y, color, shadow)); }
    @Override public void pushPose() { mark("push", null); ops.add(LadsGraphics::pushPose); }
    @Override public void popPose() { mark("pop", null); ops.add(LadsGraphics::popPose); }
    @Override public void translate(float x, float y) { mark("translate", null, x, y); ops.add(g -> g.translate(x, y)); }
    @Override public void scale(float sx, float sy) { mark("scale", null, sx, sy); ops.add(g -> g.scale(sx, sy)); }
    @Override public void enableScissor(int minX, int minY, int maxX, int maxY) { mark("scissor", null, minX, minY, maxX, maxY); ops.add(g -> g.enableScissor(minX, minY, maxX, maxY)); }
    @Override public void disableScissor() { mark("unscissor", null); ops.add(LadsGraphics::disableScissor); }
    @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) { mark("blit", Arrays.asList(texture, u, v), x, y, width, height); ops.add(g -> g.blit(texture, x, y, u, v, width, height)); }
    @Override public void drawModIcon(String id, int x, int y, int size) { mark("icon", id, x, y, size); ops.add(g -> g.drawModIcon(id, x, y, size)); }
    // These draw the game's current state each time they are replayed (the player, armour, boss bars, heads), so they are live anyway.
    @Override public void drawHead(String username, String uuid, int x, int y, int size) { mark("head", Arrays.asList(username, uuid), x, y, size); ops.add(g -> g.drawHead(username, uuid, x, y, size)); }
    @Override public void drawPlayerModel(int x, int y, int width, int height, boolean editor) { mark("model", editor, x, y, width, height); ops.add(g -> g.drawPlayerModel(x, y, width, height, editor)); }
    @Override public void drawArmorItem(int index, int x, int y, boolean preview) { mark("armor", Arrays.asList(index, preview), x, y); ops.add(g -> g.drawArmorItem(index, x, y, preview)); }
    @Override public void drawHotbarSlots(int x, int y, int slots) { mark("slots", slots, x, y); ops.add(g -> g.drawHotbarSlots(x, y, slots)); }
    @Override public void drawArmorSlot(int slot, int x, int y, boolean preview) { mark("armorSlot", Arrays.asList(slot, preview), x, y); ops.add(g -> g.drawArmorSlot(slot, x, y, preview)); }
    @Override public void drawSprite(String sprite, int x, int y, int size) { mark("sprite", sprite, x, y, size); ops.add(g -> g.drawSprite(sprite, x, y, size)); }
    @Override public void drawPotionIcon(String effectId, int iconIndex, int x, int y, int size) { mark("potionIcon", Arrays.asList(effectId, iconIndex), x, y, size); ops.add(g -> g.drawPotionIcon(effectId, iconIndex, x, y, size)); }
    @Override public void drawBossBars(int x, int y, int max, boolean names, boolean preview) { mark("bossBars", Arrays.asList(max, names, preview), x, y); ops.add(g -> g.drawBossBars(x, y, max, names, preview)); }
    // Screen-only art (the Kill Banner picker, the HUD editor backdrop): recorded like the rest, assumed drawn.
    @Override public boolean drawKillBanner(String skin, int variant, int x, int y, int width, int height) { mark("banner", Arrays.asList(skin, variant), x, y, width, height); ops.add(g -> g.drawKillBanner(skin, variant, x, y, width, height)); return true; }
    @Override public boolean drawKillBannerPreview(String skin, int variant, int x, int y, int width, int height, double clock) { mark("bannerPreview", Arrays.asList(skin, variant), x, y, width, height, clock); ops.add(g -> g.drawKillBannerPreview(skin, variant, x, y, width, height, clock)); return true; }
    @Override public boolean drawGameView(int x, int y, int width, int height) { mark("view", null, x, y, width, height); ops.add(g -> g.drawGameView(x, y, width, height)); return true; }

    @Override public int textWidth(String text) { return target.textWidth(text); }
    @Override public Object textMetricsKey() { return target.textMetricsKey(); }
    @Override public int fontHeight() { return target.fontHeight(); }
    @Override public int getScaledWidth() { return target.getScaledWidth(); }
    @Override public int getScaledHeight() { return target.getScaledHeight(); }
    @Override public int hotbarLift() { return target.hotbarLift(); }
    @Override public LadsGameBridge getGame() { return target.getGame(); }
}
