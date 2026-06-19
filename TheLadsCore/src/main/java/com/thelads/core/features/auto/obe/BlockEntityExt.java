package com.thelads.core.features.auto.obe;

public interface BlockEntityExt {
    boolean isSupportedBlockEntity();
    void isSupportedBlockEntity(boolean supported);
    boolean hasSpecialRenderer();
    void hasSpecialRenderer(boolean special);
    RenderMode renderMode();
    void renderMode(RenderMode mode);
    RenderMode renderModeDelayed();
    void renderModeDelayed(RenderMode mode);
}
