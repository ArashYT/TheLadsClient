package com.thelads.core.v26_2.feature;

import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * Consolidates CullDisplayEntities: computes transformed bounding boxes for Display entities
 * whose explicit width/height are 0 so they frustum-cull instead of rendering infinitely.
 */
public final class DisplayEntityBoundingBoxCalculator {
    private static final Vector3f[] CORNERS;

    static {
        float min = -1.0f;
        float max = 2.0f;
        CORNERS = new Vector3f[]{
                new Vector3f(min, min, min),
                new Vector3f(max, min, min),
                new Vector3f(min, max, min),
                new Vector3f(max, max, min),
                new Vector3f(min, min, max),
                new Vector3f(max, min, max),
                new Vector3f(min, max, max),
                new Vector3f(max, max, max)
        };
    }

    private DisplayEntityBoundingBoxCalculator() {}

    public static AABB getWithTransform(Display entity, Matrix4fc matrix) {
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        Vector3f temp = new Vector3f();
        for (Vector3f corner : CORNERS) {
            matrix.transformPosition(corner, temp);
            min.min(temp);
            max.max(temp);
        }
        return new AABB(
                entity.getX() + min.x,
                entity.getY() + min.y,
                entity.getZ() + min.z,
                entity.getX() + max.x,
                entity.getY() + max.y,
                entity.getZ() + max.z
        );
    }
}
