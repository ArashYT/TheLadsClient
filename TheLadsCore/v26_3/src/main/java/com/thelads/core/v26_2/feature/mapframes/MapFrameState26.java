package com.thelads.core.v26_2.feature.mapframes;

/** Duck on ItemFrameRenderState: the frame model is hidden this frame (set at extraction, read at submit). */
public interface MapFrameState26 {
    boolean lads$mapFrameHidden();
    void lads$mapFrameHidden(boolean hidden);
}
