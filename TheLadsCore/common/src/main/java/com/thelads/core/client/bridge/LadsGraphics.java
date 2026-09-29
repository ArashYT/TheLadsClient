package com.thelads.core.client.bridge;

/**
 * Universal graphics abstraction layer providing 100% rendering parity
 * across Minecraft 1.21.1 (GuiGraphics / render pipeline) and 26.2 (GuiGraphicsExtractor / Vulkan render pipeline).
 */
public interface LadsGraphics {
    void fill(int minX, int minY, int maxX, int maxY, int color);

    void drawText(String text, int x, int y, int color, boolean shadow);

    default void drawText(String text, int x, int y, int color) {
        drawText(text, x, y, color, false);
    }

    void drawCenteredText(String text, int centerX, int y, int color, boolean shadow);

    default void drawCenteredText(String text, int centerX, int y, int color) {
        drawCenteredText(text, centerX, y, color, false);
    }

    int textWidth(String text);

    int fontHeight();

    void pushPose();

    void popPose();

    void translate(float x, float y);

    void scale(float sx, float sy);

    void enableScissor(int minX, int minY, int maxX, int maxY);

    void disableScissor();

    void blit(String texture, int x, int y, int u, int v, int width, int height);

    void drawHead(String username, String uuid, int x, int y, int size);

    /** Render the actual local player with the native entity renderer, when available. */
    default void drawPlayerModel(int x, int y, int width, int height, boolean editor) {}

    int getScaledWidth();

    int getScaledHeight();

    default LadsGameBridge getGame() {
        return LadsGameBridge.get();
    }
}
