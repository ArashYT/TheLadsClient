package com.thelads.core.client.bridge;

/**
 * Universal graphics abstraction layer providing 100% rendering parity
 * across Minecraft 1.21.1 (GuiGraphics / render pipeline) and 26.2 (GuiGraphicsExtractor / Vulkan render pipeline).
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

    /** Render the actual local player with the native entity renderer, when available. */
    default void drawPlayerModel(int x, int y, int width, int height, boolean editor) {}

    default void drawArmorItem(int index, int x, int y, boolean preview) {}

    default void drawBossBars(int x,int y,int max,boolean names,boolean preview) {}

    int getScaledWidth();

    int getScaledHeight();

    default LadsGameBridge getGame() {
        return LadsGameBridge.get();
    }
}
