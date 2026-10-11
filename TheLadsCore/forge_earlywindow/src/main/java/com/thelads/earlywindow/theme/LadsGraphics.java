package com.thelads.earlywindow.theme;

/**
 * The part of the Core's LadsGraphics that LoadingScreenTheme draws with. build.gradle copies LoadingScreenTheme and LadsPalette
 * from TheLadsCore/common into this package unchanged (only the package line), so the early window always draws the same
 * screen as the Core's in-game loading overlay. A relocated copy, never the Core's own package: this jar sits in Forge's boot
 * module layer and the Core (through Connector) in the game layer, and one package in two readable modules fails module resolution.
 */
public interface LadsGraphics {
    void fill(int minX, int minY, int maxX, int maxY, int color);

    void pushPose();

    void popPose();

    void translate(float x, float y);

    void scale(float sx, float sy);
}
