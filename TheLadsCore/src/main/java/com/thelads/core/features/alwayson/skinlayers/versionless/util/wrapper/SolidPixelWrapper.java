package com.thelads.core.features.alwayson.skinlayers.versionless.util.wrapper;

import com.thelads.core.features.alwayson.skinlayers.versionless.ModBase;
import com.thelads.core.features.alwayson.skinlayers.versionless.util.Direction;
import java.util.HashSet;

public class SolidPixelWrapper {
    private static final float pixelSize = 1.0f;

    public static ModelBuilder wrapBox(ModelBuilder builder, TextureData natImage, int width, int height, int depth, int textureU, int textureV, boolean topPivot, float rotationOffset) {
        builder.textureSize(natImage.getWidth(), natImage.getHeight());
        float staticXOffset = (float) (-width) / 2.0f;
        float staticYOffset = topPivot ? rotationOffset : (float) (-height) + rotationOffset;
        float staticZOffset = (float) (-depth) / 2.0f;
        Position staticOffset = new Position(staticXOffset, staticYOffset, staticZOffset);
        Dimensions dimensions = new Dimensions(width, height, depth);
        UV textureUV = new UV(textureU, textureV);
        try {
            for (Direction face : Direction.values()) {
                UV sizeUV = SolidPixelWrapper.getSizeUV(dimensions, face);
                for (int u = 0; u < sizeUV.u; ++u) {
                    for (int v = 0; v < sizeUV.v; ++v) {
                        SolidPixelWrapper.addPixel(natImage, builder, staticOffset, face, dimensions, new UV(u, v), textureUV, sizeUV);
                    }
                }
            }
        } catch (Exception ex) {
            ModBase.LOGGER.error("Error while creating 3d skin model. Please report on the Github/Discord.", ex);
            return null;
        }
        if (ModBase.config.fastRender) {
            builder.uv(textureU, textureV).addVanillaBox(staticXOffset, staticYOffset, staticZOffset, width, height, depth);
        }
        return builder;
    }

    private static UV getSizeUV(Dimensions dimensions, Direction face) {
        if (face == Direction.DOWN || face == Direction.UP) {
            return new UV(dimensions.width, dimensions.depth);
        }
        if (face == Direction.NORTH || face == Direction.SOUTH) {
            return new UV(dimensions.width, dimensions.height);
        }
        return new UV(dimensions.depth, dimensions.height);
    }

    private static UV getOnTextureUV(UV textureUV, UV onFaceUV, Dimensions dimensions, Direction face) {
        if (face == Direction.DOWN) {
            return new UV(textureUV.u + dimensions.depth + onFaceUV.u, textureUV.v + onFaceUV.v);
        }
        if (face == Direction.UP) {
            return new UV(textureUV.u + dimensions.width + dimensions.depth + onFaceUV.u, textureUV.v + onFaceUV.v);
        }
        if (face == Direction.NORTH) {
            return new UV(textureUV.u + dimensions.depth + onFaceUV.u, textureUV.v + dimensions.depth + onFaceUV.v);
        }
        if (face == Direction.SOUTH) {
            return new UV(textureUV.u + dimensions.depth + dimensions.width + dimensions.depth + onFaceUV.u, textureUV.v + dimensions.depth + onFaceUV.v);
        }
        if (face == Direction.WEST) {
            return new UV(textureUV.u + onFaceUV.u, textureUV.v + dimensions.depth + onFaceUV.v);
        }
        return new UV(textureUV.u + dimensions.depth + dimensions.width + onFaceUV.u, textureUV.v + dimensions.depth + onFaceUV.v);
    }

    private static VoxelPosition UVtoXYZ(UV onFaceUV, Dimensions dimensions, Direction face) {
        if (face == Direction.DOWN) {
            return new VoxelPosition(onFaceUV.u, 0, dimensions.depth - 1 - onFaceUV.v);
        }
        if (face == Direction.UP) {
            return new VoxelPosition(onFaceUV.u, dimensions.height - 1, dimensions.depth - 1 - onFaceUV.v);
        }
        if (face == Direction.NORTH) {
            return new VoxelPosition(onFaceUV.u + 0, onFaceUV.v, 0);
        }
        if (face == Direction.SOUTH) {
            return new VoxelPosition(dimensions.width - 1 - onFaceUV.u, onFaceUV.v, dimensions.depth - 1);
        }
        if (face == Direction.WEST) {
            return new VoxelPosition(0, onFaceUV.v, dimensions.depth - 1 - onFaceUV.u);
        }
        return new VoxelPosition(dimensions.width - 1, onFaceUV.v, onFaceUV.u + 0);
    }

    private static UV XYZtoUV(VoxelPosition voxelPosition, Dimensions dimensions, Direction face) {
        if (face == Direction.DOWN || face == Direction.UP) {
            return new UV(voxelPosition.x, dimensions.depth - 1 - voxelPosition.z);
        }
        if (face == Direction.NORTH) {
            return new UV(voxelPosition.x + 0, voxelPosition.y);
        }
        if (face == Direction.SOUTH) {
            return new UV(dimensions.width - 1 - voxelPosition.x, voxelPosition.y);
        }
        if (face == Direction.WEST) {
            return new UV(dimensions.depth - 1 - voxelPosition.z, voxelPosition.y);
        }
        return new UV(voxelPosition.z + 0, voxelPosition.y);
    }

    private static void addPixel(TextureData natImage, ModelBuilder cubes, Position staticOffset, Direction face, Dimensions dimensions, UV onFaceUV, UV textureUV, UV sizeUV) {
        UV onTextureUV = SolidPixelWrapper.getOnTextureUV(textureUV, onFaceUV, dimensions, face);
        if (!natImage.isPresent(onTextureUV)) {
            return;
        }
        VoxelPosition voxelPosition = SolidPixelWrapper.UVtoXYZ(onFaceUV, dimensions, face);
        Position position = new Position(staticOffset.x + (float) voxelPosition.x, staticOffset.y + (float) voxelPosition.y, staticOffset.z + (float) voxelPosition.z);
        boolean solidPixel = natImage.isSolid(onTextureUV);
        HashSet<Direction> hide = new HashSet<>();
        HashSet<Direction[]> corners = new HashSet<>();
        boolean isOnBorder = false;
        boolean backsideOverlaps = false;
        for (Direction neighbourFace : Direction.values()) {
            if (neighbourFace.getAxis() == face.getAxis()) continue;
            VoxelPosition neighbourVoxelPosition = new VoxelPosition(voxelPosition.x + neighbourFace.getStepX(), voxelPosition.y + neighbourFace.getStepY(), voxelPosition.z + neighbourFace.getStepZ());
            UV neighbourOnFaceUV = SolidPixelWrapper.XYZtoUV(neighbourVoxelPosition, dimensions, face);
            if (SolidPixelWrapper.isOnFace(neighbourOnFaceUV, sizeUV)) {
                if (natImage.isPresent(SolidPixelWrapper.getOnTextureUV(textureUV, neighbourOnFaceUV, dimensions, face))) {
                    if (solidPixel && !natImage.isSolid(SolidPixelWrapper.getOnTextureUV(textureUV, neighbourOnFaceUV, dimensions, face))) continue;
                    hide.add(neighbourFace);
                    continue;
                }
                VoxelPosition farNeighbourVoxelPosition = new VoxelPosition(neighbourVoxelPosition.x + neighbourFace.getStepX(), neighbourVoxelPosition.y + neighbourFace.getStepY(), neighbourVoxelPosition.z + neighbourFace.getStepZ());
                UV farNeighbourOnFaceUV = SolidPixelWrapper.XYZtoUV(farNeighbourVoxelPosition, dimensions, face);
                if (SolidPixelWrapper.isOnFace(farNeighbourOnFaceUV, sizeUV) || !natImage.isPresent(SolidPixelWrapper.getOnTextureUV(textureUV, farNeighbourOnFaceUV = SolidPixelWrapper.XYZtoUV(farNeighbourVoxelPosition, dimensions, neighbourFace), dimensions, neighbourFace)) || solidPixel && !natImage.isSolid(SolidPixelWrapper.getOnTextureUV(textureUV, farNeighbourOnFaceUV, dimensions, neighbourFace))) continue;
                hide.add(neighbourFace);
                continue;
            }
            isOnBorder = true;
            neighbourOnFaceUV = SolidPixelWrapper.XYZtoUV(voxelPosition, dimensions, neighbourFace);
            if (natImage.isPresent(SolidPixelWrapper.getOnTextureUV(textureUV, neighbourOnFaceUV, dimensions, neighbourFace))) {
                backsideOverlaps = true;
                hide.add(neighbourFace);
                corners.add(new Direction[]{face.getOpposite(), neighbourFace});
                continue;
            }
            UV downNeighbourOnFaceUV = SolidPixelWrapper.XYZtoUV(new VoxelPosition(voxelPosition.x - face.getStepX(), voxelPosition.y - face.getStepY(), voxelPosition.z - face.getStepZ()), dimensions, neighbourFace);
            if (!natImage.isPresent(SolidPixelWrapper.getOnTextureUV(textureUV, downNeighbourOnFaceUV, dimensions, neighbourFace))) continue;
            backsideOverlaps = true;
        }
        if (!isOnBorder || backsideOverlaps) {
            hide.add(face.getOpposite());
        }
        if (ModBase.config.fastRender) {
            hide.add(face);
        }
        cubes.uv(onTextureUV.u, onTextureUV.v).addBox(position.x, position.y, position.z, 1.0f, hide.toArray(new Direction[0]), corners.toArray(new Direction[0][0]));
    }

    private static boolean isOnFace(UV onFaceUV, UV sizeUV) {
        return onFaceUV.u >= 0 && onFaceUV.u < sizeUV.u && onFaceUV.v >= 0 && onFaceUV.v < sizeUV.v;
    }

    public record Position(float x, float y, float z) {
    }

    public record Dimensions(int width, int height, int depth) {
    }

    public record UV(int u, int v) {
    }

    public record VoxelPosition(int x, int y, int z) {
    }
}
