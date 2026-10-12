package com.thelads.core.client;

/**
 * Text drawn in the world with a shadow (Nametags' Text Shadow): how far in front of its shadow the text goes, in the text's own z
 * units. Minecraft moves it 0.03 along its z, which points away from the camera in 1.20.1's nametag space (the shadow covered the
 * text) and is a depth step a depth buffer can't hold past a few blocks on 26.x (the shadow flickered through it as black lines while
 * the camera moved). Here it points at the camera, which sits at the origin of the pose, and grows with the squared distance to
 * about four steps of a 24-bit depth buffer at Minecraft's 0.05 near plane (one step there is d^2 / 840000 blocks).
 */
public final class NametagDepth {
    static final float VANILLA = 0.03F;
    /** QA only (FixesQa): Minecraft's own 0.03, to photograph the black lines next to the fix. */
    public static volatile boolean qaVanilla;

    private NametagDepth() {}

    /** {@code z*}: the pose's z column (the text's z axis in camera space); {@code t*}: its translation (where the text is). */
    public static float textOffset(float zx, float zy, float zz, float tx, float ty, float tz) {
        float scale = (float) Math.sqrt(zx * zx + zy * zy + zz * zz);
        if (scale == 0 || qaVanilla) return VANILLA;
        float offset = Math.max(VANILLA, (tx * tx + ty * ty + tz * tz) / 200_000F / scale);
        return zx * tx + zy * ty + zz * tz > 0 ? -offset : offset;
    }
}
