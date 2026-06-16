package com.thelads.core.features.alwayson.entityculling;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DebugCollector {
    private boolean running = false;
    public boolean isRunning() {
        return this.running;
    }
    public void addEntity(Entity entity, boolean rendered, boolean ignoredCulling) {}
    public void addBlockEntity(BlockEntity blockEntity, boolean rendered) {}
}
