package com.thelads.core.client;

/** Shared numbers for the screen effects (Motion Blur, Menu Blur) each version draws. */
public final class ScreenEffects {
    /** Menu Blur's screen kinds (MenuBlurModule.radius). */
    public static final int INVENTORY = 0, PAUSE = 1, OTHER = 2;
    /**
     * The most of the last frame one frame keeps. The picture is 8 bits per channel, so a blend keeping f of the old frame leaves
     * differences under 0.5 / (1 - f) levels standing for good (ghosts that never fade): at 0.9 that is under 5 of 255.
     */
    static final float MAX_RETAIN = 0.9F;

    private ScreenEffects() {}

    /**
     * Motion Blur: how much of the last frame this frame keeps. Strength 1-10 keeps 0.09-0.9 per 1/60 s, so the trail lasts as long
     * at any frame rate (up to {@link #MAX_RETAIN} per frame). A first frame or a stall of a quarter second or more keeps nothing.
     */
    public static float motionRetain(double strength, long frameNanos) {
        if (frameNanos <= 0 || frameNanos >= 250_000_000L) return 0;
        double perSixtieth = Math.max(1, Math.min(10, strength)) * 0.09;
        return (float) Math.min(MAX_RETAIN, Math.pow(perSixtieth, frameNanos / (1e9 / 60)));
    }
}
