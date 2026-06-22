package com.thelads.core.modules;

import com.thelads.core.config.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.Level;

public class XaeroWorldMapModule extends Module {
    private static XaeroWorldMapModule instance;
    private DynamicTexture mapTexture;
    private Identifier textureLocation;
    private int mapCenterX = 0;
    private int mapCenterZ = 0;
    private boolean needsUpdate = true;

    public XaeroWorldMapModule() {
        super("XaeroWorldmap", "Self-writing fullscreen map of the world.");
        instance = this;
    }

    public static XaeroWorldMapModule getInstance() {
        return instance;
    }

    public Identifier getTextureLocation() {
        return textureLocation;
    }

    public int getMapCenterX() {
        return mapCenterX;
    }

    public int getMapCenterZ() {
        return mapCenterZ;
    }

    public void updateMap(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        Level level = mc.level;
        int px = (int) mc.player.getX();
        int pz = (int) mc.player.getZ();

        mapCenterX = px;
        mapCenterZ = pz;

        int size = 256;
        NativeImage image = new NativeImage(size, size, false);

        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                int worldX = px - (size / 2) + dx;
                int worldZ = pz - (size / 2) + dz;

                int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
                BlockPos pos = new BlockPos(worldX, Math.max(0, topY - 1), worldZ);
                BlockState state = level.getBlockState(pos);
                MapColor color = state.getMapColor(level, pos);

                int abgr = 0xFF000000;
                if (color != null) {
                    int rgb = color.col;
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    abgr = 0xFF000000 | (b << 16) | (g << 8) | r;
                }

                int heightDiff = topY - 64;
                if (heightDiff > 0) {
                    abgr = brighten(abgr, Math.min(30, heightDiff * 2));
                } else if (heightDiff < 0) {
                    abgr = darken(abgr, Math.min(30, -heightDiff * 2));
                }

                image.setPixel(dx, dz, abgr);
            }
        }

        if (mapTexture != null) {
            mapTexture.close();
        }

        mapTexture = new DynamicTexture(() -> "lads_world_map", image);
        if (textureLocation == null) {
            textureLocation = Identifier.fromNamespaceAndPath("theladscore", "lads_world_map");
            mc.getTextureManager().register(textureLocation, mapTexture);
        } else {
            mc.getTextureManager().register(textureLocation, mapTexture);
        }
        needsUpdate = false;
    }

    private int brighten(int abgr, int amt) {
        int r = abgr & 0xFF;
        int g = (abgr >> 8) & 0xFF;
        int b = (abgr >> 16) & 0xFF;
        r = Math.min(255, r + amt);
        g = Math.min(255, g + amt);
        b = Math.min(255, b + amt);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    private int darken(int abgr, int amt) {
        int r = abgr & 0xFF;
        int g = (abgr >> 8) & 0xFF;
        int b = (abgr >> 16) & 0xFF;
        r = Math.max(0, r - amt);
        g = Math.max(0, g - amt);
        b = Math.max(0, b - amt);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    public void triggerUpdate() {
        needsUpdate = true;
    }

    public boolean needsUpdate() {
        return needsUpdate;
    }
}
