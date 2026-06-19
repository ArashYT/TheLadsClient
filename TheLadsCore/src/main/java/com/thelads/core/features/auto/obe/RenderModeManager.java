package com.thelads.core.features.auto.obe;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RenderModeManager {

    public static boolean isTerrain(BlockEntityExt ext) {
        if (ext == null) {
            return false;
        }
        return ext.renderMode() == RenderMode.TERRAIN;
    }

    public static void setRenderMode(BlockEntityExt ext, RenderMode mode, BlockPos pos) {
        if (ext.renderMode() != mode) {
            setDirty(pos);
            ext.renderMode(mode);
            ext.renderModeDelayed(mode);
        }
    }

    public static boolean shouldRenderEntity(BlockEntityExt ext) {
        if (ext == null) {
            return true;
        }
        return ext.renderMode() == RenderMode.ENTITY || ext.renderModeDelayed() == RenderMode.ENTITY;
    }

    public static void setRenderModeDelayed(BlockEntityExt ext, RenderMode mode, BlockPos pos) {
        if (ext.renderModeDelayed() != mode) {
            ext.renderModeDelayed(mode);
            setDirty(pos);
        }
    }

    private static void setDirty(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(() -> setDirty(pos));
            return;
        }
        if (mc.levelRenderer != null) {
            mc.levelRenderer.blockChanged(null, pos, null, null, 8);
        }
    }

    public static void updateBlockEntity(BlockEntityExt ext, BlockEntity be) {
        if (ext.isSupportedBlockEntity()) {
            if (ext.renderMode() != ext.renderModeDelayed()) {
                ext.renderMode(ext.renderModeDelayed());
            }
        }
    }
}
