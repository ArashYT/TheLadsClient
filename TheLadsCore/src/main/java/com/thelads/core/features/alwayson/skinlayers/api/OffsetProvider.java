package com.thelads.core.features.alwayson.skinlayers.api;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.features.alwayson.skinlayers.SkinLayersModBase;

public interface OffsetProvider {
    OffsetProvider SKULL = OffsetProvider.createVanilla(Shape.HEAD, false, false, true);
    OffsetProvider HEAD = OffsetProvider.createVanilla(Shape.HEAD);
    OffsetProvider LEFT_LEG = OffsetProvider.createVanilla(Shape.LEGS);
    OffsetProvider RIGHT_LEG = OffsetProvider.createVanilla(Shape.LEGS);
    OffsetProvider LEFT_ARM = OffsetProvider.createVanilla(Shape.ARMS);
    OffsetProvider LEFT_ARM_SLIM = OffsetProvider.createVanilla(Shape.ARMS_SLIM);
    OffsetProvider RIGHT_ARM = OffsetProvider.createVanilla(Shape.ARMS, true, false, false);
    OffsetProvider RIGHT_ARM_SLIM = OffsetProvider.createVanilla(Shape.ARMS_SLIM, true, false, false);
    OffsetProvider FIRSTPERSON_LEFT_ARM = OffsetProvider.createVanilla(Shape.ARMS, false, true, false);
    OffsetProvider FIRSTPERSON_LEFT_ARM_SLIM = OffsetProvider.createVanilla(Shape.ARMS_SLIM, false, true, false);
    OffsetProvider FIRSTPERSON_RIGHT_ARM = OffsetProvider.createVanilla(Shape.ARMS, true, true, false);
    OffsetProvider FIRSTPERSON_RIGHT_ARM_SLIM = OffsetProvider.createVanilla(Shape.ARMS_SLIM, true, true, false);
    OffsetProvider BODY = OffsetProvider.createVanilla(Shape.BODY);

    void applyOffset(PoseStack poseStack, Mesh mesh);

    private static OffsetProvider createVanilla(Shape shape) {
        return OffsetProvider.createVanilla(shape, false, false, false);
    }

    private static OffsetProvider createVanilla(Shape shape, boolean mirrored, boolean firstperson, boolean skull) {
        return (stack, mesh) -> {
            float pixelScaling = SkinLayersModBase.config.baseVoxelSize;
            float heightScaling = 1.035f;
            float widthScaling = SkinLayersModBase.config.baseVoxelSize;
            if (firstperson) {
                pixelScaling = SkinLayersModBase.config.firstPersonPixelScaling;
                widthScaling = SkinLayersModBase.config.firstPersonPixelScaling;
            }
            float x = 0.0f;
            float y = 0.0f;
            if (shape == Shape.ARMS) {
                x = 0.998f;
            } else if (shape == Shape.ARMS_SLIM) {
                x = 0.499f;
            }
            if (shape == Shape.BODY) {
                widthScaling = SkinLayersModBase.config.bodyVoxelWidthSize;
            }
            if (mirrored) {
                x *= -1.0f;
            }
            if (shape == Shape.HEAD) {
                float voxelSize = SkinLayersModBase.config.headVoxelSize;
                if (skull) {
                    voxelSize = SkinLayersModBase.config.skullVoxelSize;
                }
                stack.translate(0.0, -0.25, 0.0);
                stack.scale(voxelSize, voxelSize, voxelSize);
                stack.translate(0.0, 0.25, 0.0);
                stack.translate(0.0, -0.04, 0.0);
            } else {
                stack.scale(widthScaling, heightScaling, pixelScaling);
                y = shape.yOffsetMagicValue();
            }
            mesh.setPosition(x, y, 0.0f);
        };
    }
}
