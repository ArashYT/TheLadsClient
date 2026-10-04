package com.thelads.core.client;

/**
 * No see-through seams in item models made from a texture (swords, sticks, bows: the "generated" models). Each edge pixel
 * gets a thin quad between the item's front and back faces; where one meets the front or back face, or the next edge quad,
 * rounding can leave a hairline the background shows through. As Forge's ItemLayerModel does, each edge quad sits a hair
 * inside its pixel and reaches a hair past its ends and past both faces, so the faces overlap instead of meeting edge to
 * edge. Always on; a hair is under 1/100 of a pixel of the texture.
 */
public final class ItemModelGaps {
    /** In model units (16 a block): how far an edge quad sits inside its pixel, and how far it reaches past its edges. */
    static final float INSET = 16 * 3e-4f, REACH = 16 * 4.5e-4f;

    private ItemModelGaps() {}

    /**
     * Moves an edge quad given by two corners (x, y, z in model units, changed in place) whose outward normal is
     * (normalX, normalY, 0): inside its pixel along the normal, and longer on the other two axes.
     */
    public static void overlap(float[] from, float[] to, int normalX, int normalY) {
        for (int axis = 0; axis < 3; axis++) {
            int normal = axis == 0 ? normalX : axis == 1 ? normalY : 0;
            if (normal != 0) {
                from[axis] -= normal * INSET;
                to[axis] -= normal * INSET;
            } else {
                float grow = from[axis] <= to[axis] ? REACH : -REACH;
                from[axis] -= grow;
                to[axis] += grow;
            }
        }
    }
}
