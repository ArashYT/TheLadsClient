package com.thelads.core.features.alwayson.skinlayers.render;

import com.google.common.collect.Lists;
import com.thelads.core.features.alwayson.skinlayers.SkinLayersModBase;
import com.thelads.core.features.alwayson.skinlayers.api.BoxBuilder;
import com.thelads.core.features.alwayson.skinlayers.api.SkinLayersAPI;
import com.thelads.core.features.alwayson.skinlayers.util.SodiumWorkaround;
import com.thelads.core.features.alwayson.skinlayers.versionless.render.CustomizableCube;
import com.thelads.core.features.alwayson.skinlayers.versionless.util.wrapper.ModelBuilder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.core.Direction;

public class CustomizableCubeListBuilder implements ModelBuilder {
    private final List<CustomizableCube> cubes = Lists.newArrayList();
    private final List<ModelPart.Cube> vanillaCubes = Lists.newArrayList();
    private int u;
    private int v;
    private boolean mirror;
    private int textureWidth = 64;
    private int textureHeight = 64;

    public static ModelBuilder create() {
        return new CustomizableCubeListBuilder();
    }

    @Override
    public ModelBuilder textureSize(int width, int height) {
        this.textureWidth = width;
        this.textureHeight = height;
        return this;
    }

    @Override
    public ModelBuilder uv(int u, int v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public ModelBuilder mirror(boolean bl) {
        this.mirror = bl;
        return this;
    }

    public List<CustomizableCube> getCubes() {
        return this.cubes;
    }

    public List<ModelPart.Cube> getVanillaCubes() {
        return this.vanillaCubes;
    }

    @Override
    public ModelBuilder addBox(float x, float y, float z, float pixelSize, com.thelads.core.features.alwayson.skinlayers.versionless.util.Direction[] hide, com.thelads.core.features.alwayson.skinlayers.versionless.util.Direction[][] corners) {
        if (!SkinLayersModBase.config.irisCompatibilityMode) {
            this.cubes.add(new CustomizableCube(this.u, this.v, (float) (this.mirror ? -1 : 1) * x, y, z, pixelSize, pixelSize, pixelSize, 0.0f, 0.0f, 0.0f, this.mirror, this.textureWidth, this.textureHeight, hide, corners));
        } else {
            for (com.thelads.core.features.alwayson.skinlayers.versionless.util.Direction dir : com.thelads.core.features.alwayson.skinlayers.versionless.util.Direction.values()) {
                boolean skip = false;
                for (com.thelads.core.features.alwayson.skinlayers.versionless.util.Direction hideDir : hide) {
                    if (hideDir == dir) {
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;
                int uO = this.u;
                int vO = this.v;
                switch (dir) {
                    case DOWN: {
                        uO = (int) ((float) uO - pixelSize);
                        break;
                    }
                    case UP: {
                        uO = (int) ((float) uO - pixelSize * 2.0f);
                        break;
                    }
                    case NORTH: {
                        uO = (int) ((float) uO - pixelSize);
                        vO = (int) ((float) vO - pixelSize);
                        break;
                    }
                    case SOUTH: {
                        uO = (int) ((float) uO - pixelSize * 3.0f);
                        vO = (int) ((float) vO - pixelSize);
                        break;
                    }
                    case WEST: {
                        vO = (int) ((float) vO - pixelSize);
                        break;
                    }
                    case EAST: {
                        uO = (int) ((float) uO - pixelSize * 2.0f);
                        vO = (int) ((float) vO - pixelSize);
                        break;
                    }
                }
                CubeListBuilder cubeList = CubeListBuilder.create();
                Direction mcDir;
                switch (dir) {
                    case UP:
                        mcDir = Direction.UP;
                        break;
                    case DOWN:
                        mcDir = Direction.DOWN;
                        break;
                    case NORTH:
                        mcDir = Direction.NORTH;
                        break;
                    case EAST:
                        if (SodiumWorkaround.applySodiumWorkaround()) {
                            mcDir = Direction.WEST;
                        } else {
                            mcDir = Direction.EAST;
                        }
                        break;
                    case WEST:
                        if (SodiumWorkaround.applySodiumWorkaround()) {
                            mcDir = Direction.EAST;
                        } else {
                            mcDir = Direction.WEST;
                        }
                        break;
                    case SOUTH:
                        mcDir = Direction.SOUTH;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected direction: " + dir);
                }
                cubeList.texOffs(uO, vO).mirror(this.mirror).addBox(x, y, z, pixelSize, pixelSize, pixelSize, new HashSet<>(Arrays.asList(mcDir)));
                this.vanillaCubes.add(((CubeDefinition) cubeList.getCubes().get(0)).bake(this.textureWidth, this.textureHeight));
            }
        }
        return this;
    }

    @Override
    public ModelBuilder addVanillaBox(float x, float y, float z, float width, float height, float depth) {
        if (this.mirror) {
            x = -1.0f;
        }
        this.vanillaCubes.add(SkinLayersAPI.getBoxBuilder().build(new BoxBuilder.BoxDefinition(this.u, this.v, this.mirror, x, y, z, width, height, depth, this.textureWidth, this.textureHeight)));
        return this;
    }

    @Override
    public boolean isEmpty() {
        return this.getCubes().isEmpty() && this.getVanillaCubes().isEmpty();
    }
}
