package com.thelads.core.client.bridge;

/**
 * Universal graphics abstraction layer providing 100% rendering parity
 * across Minecraft 1.21.11 (GuiGraphics / render pipeline) and 26.2 (GuiGraphicsExtractor / Vulkan render pipeline).
 */
public interface LadsGraphics {
    void fill(int minX, int minY, int maxX, int maxY, int color);

    void drawText(String text, int x, int y, int color, boolean shadow);

    default void drawText(String text, int x, int y, int color) {
        drawText(text, x, y, color, com.thelads.core.config.HudSettings.getInstance().isTextShadow());
    }

    void drawCenteredText(String text, int centerX, int y, int color, boolean shadow);

    default void drawCenteredText(String text, int centerX, int y, int color) {
        drawCenteredText(text, centerX, y, color, com.thelads.core.config.HudSettings.getInstance().isTextShadow());
    }

    int textWidth(String text);

    /** Identity changes whenever font metrics change; unknown adapters opt out of caching. */
    default Object textMetricsKey() { return null; }

    int fontHeight();

    void pushPose();

    void popPose();

    void translate(float x, float y);

    void scale(float sx, float sy);

    void enableScissor(int minX, int minY, int maxX, int maxY);

    void disableScissor();

    void blit(String texture, int x, int y, int u, int v, int width, int height);

    default void drawModIcon(String id, int x, int y, int size) {
        fill(x, y, x + size, y + size, 0xFF532131);
        drawCenteredText(id.isEmpty() ? "?" : id.substring(0, 1).toUpperCase(java.util.Locale.ROOT), x + size / 2, y + (size - fontHeight()) / 2, -1);
    }

    void drawHead(String username, String uuid, int x, int y, int size);

    /**
     * Kill Banner picker art: a skin's settled one-kill banner ("base", "reaver" or "rogue") in one of its variants, fitted
     * and centred in the box. False where the version cannot draw it (the picker then shows the name only).
     */
    default boolean drawKillBanner(String skin, int variant, int x, int y, int width, int height) { return false; }

    /** Render the actual local player with the native entity renderer, when available. */
    default void drawPlayerModel(int x, int y, int width, int height, boolean editor) {}

    default void drawArmorItem(int index, int x, int y, boolean preview) {}

    /** The left part of vanilla's hotbar frame with this many slots (each 20 px) and its right edge: 2 + 20 * slots by 22 px. */
    default void drawHotbarSlots(int x, int y, int slots) {}

    /** Armour slot 0 head to 3 feet: the worn piece with its durability bar, or a faint empty-slot icon (preview: a sample set). */
    default void drawArmorSlot(int slot, int x, int y, boolean preview) {}

    default void drawBossBars(int x,int y,int max,boolean names,boolean preview) {}

    /** A GUI atlas sprite such as "voicechat:icons/microphone", size by size; nothing where the version has no such sprite. */
    default void drawSprite(String sprite, int x, int y, int size) {}

    /**
     * HUD editor: this frame's game view (the world and the vanilla HUD, without the Lads HUD) scaled into the box.
     * False where no world is shown or the version cannot copy it; the editor then keeps its plain backdrop.
     */
    default boolean drawGameView(int x, int y, int width, int height) { return false; }

    int getScaledWidth();

    int getScaledHeight();

    /** Pixels the hotbar is lifted above the screen bottom (Hovering Hotbar); hotbar-attached elements follow it. */
    default int hotbarLift() { return 0; }

    default LadsGameBridge getGame() {
        return LadsGameBridge.get();
    }
}
