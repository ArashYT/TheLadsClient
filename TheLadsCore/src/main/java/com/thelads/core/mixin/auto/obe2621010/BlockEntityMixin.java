package com.thelads.core.mixin.auto.obe2621010;

import com.thelads.core.features.auto.obe.BlockEntityExt;
import com.thelads.core.features.auto.obe.RenderMode;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements BlockEntityExt {
    @Unique
    private RenderMode thelads$renderMode = RenderMode.TERRAIN;
    @Unique
    private RenderMode thelads$renderModeDelayed = RenderMode.TERRAIN;
    @Unique
    private boolean thelads$isSupportedBlockEntity = false;
    @Unique
    private boolean thelads$hasSpecialRenderer = false;

    @Override
    public boolean isSupportedBlockEntity() {
        return this.thelads$isSupportedBlockEntity;
    }

    @Override
    public void isSupportedBlockEntity(boolean supported) {
        this.thelads$isSupportedBlockEntity = supported;
    }

    @Override
    public boolean hasSpecialRenderer() {
        return this.thelads$hasSpecialRenderer;
    }

    @Override
    public void hasSpecialRenderer(boolean special) {
        this.thelads$hasSpecialRenderer = special;
    }

    @Override
    public RenderMode renderMode() {
        return this.thelads$renderMode;
    }

    @Override
    public void renderMode(RenderMode mode) {
        this.thelads$renderMode = mode;
        this.thelads$renderModeDelayed = mode;
    }

    @Override
    public RenderMode renderModeDelayed() {
        return this.thelads$renderModeDelayed;
    }

    @Override
    public void renderModeDelayed(RenderMode mode) {
        this.thelads$renderModeDelayed = mode;
    }
}
