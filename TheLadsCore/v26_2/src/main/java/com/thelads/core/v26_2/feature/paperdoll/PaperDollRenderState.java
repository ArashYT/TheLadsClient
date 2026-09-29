package com.thelads.core.v26_2.feature.paperdoll;

/** Marker belongs to this extracted doll state, never the shared player or world render state. */
public interface PaperDollRenderState {
    int ladsPaperDollAlpha();
    void ladsSetPaperDollAlpha(int value);
}
