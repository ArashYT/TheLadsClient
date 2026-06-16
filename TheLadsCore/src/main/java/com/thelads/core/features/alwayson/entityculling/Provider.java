/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.level.block.LeavesBlock
 *  net.minecraft.world.level.block.state.BlockState
 */
package com.thelads.core.features.alwayson.entityculling;

import com.thelads.core.features.alwayson.entityculling.occlusionculling.DataProvider;
import com.thelads.core.features.alwayson.entityculling.EntityCullingModBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public class Provider
implements DataProvider {
    private final Minecraft client = Minecraft.getInstance();
    private ClientLevel world = null;

    @Override
    public boolean prepareChunk(int chunkX, int chunkZ) {
        this.world = this.client.level;
        return this.world != null;
    }

    @Override
    public boolean isOpaqueFullCube(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = this.world.getBlockState(pos);
        if (EntityCullingModBase.instance.config.solidLeaves && state.getBlock() instanceof LeavesBlock) {
            return true;
        }
        return state.isSolidRender();
    }

    @Override
    public void cleanup() {
        this.world = null;
    }
}

