package com.thelads.core.v1_20_1.game.anim;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/** Whether a baked item model is a flat icon (a generated item model, one pixel thick): the 1.7 recipes place flat items only. */
public final class ModelShape1201 {
    /** Thicker than this (in blocks) is a block or a 3D model; a generated item is 1/16 thick. */
    private static final float FLAT = 0.1f;
    private static final Map<BakedModel, Boolean> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private ModelShape1201() {}

    public static boolean flat(BakedModel model) {
        if (model == null || model.isCustomRenderer() || model.usesBlockLight()) return false;
        return CACHE.computeIfAbsent(model, ModelShape1201::thin);
    }

    private static boolean thin(BakedModel model) {
        RandomSource random = RandomSource.create(42L);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        boolean any = false;
        for (int side = -1; side < Direction.values().length; side++) {
            Direction direction = side < 0 ? null : Direction.values()[side];
            for (BakedQuad quad : model.getQuads(null, direction, random)) {
                int[] vertices = quad.getVertices();
                for (int vertex = 0; vertex + 2 < vertices.length; vertex += vertices.length / 4) {
                    float z = Float.intBitsToFloat(vertices[vertex + 2]);
                    min = Math.min(min, z);
                    max = Math.max(max, z);
                    any = true;
                }
            }
        }
        return any && max - min < FLAT;
    }
}
