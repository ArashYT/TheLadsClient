package com.thelads.core.mixin.alwayson.skinlayers;

import com.thelads.core.features.alwayson.skinlayers.accessor.SkullSettings;
import com.thelads.core.features.alwayson.skinlayers.api.Mesh;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SkullBlockEntity.class)
public class SkullBlockEntityMixin implements SkullSettings {
    @Unique
    private Mesh hatModel = null;
    @Unique
    private boolean initialized = false;
    @Unique
    private Identifier lastTexture = null;

    @Override
    public Mesh getHeadLayers() {
        return this.hatModel;
    }

    @Override
    public void setupHeadLayers(Mesh box) {
        this.hatModel = box;
    }

    @Override
    public boolean initialized() {
        return this.initialized;
    }

    @Override
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    @Override
    public void setLastTexture(Identifier texture) {
        this.lastTexture = texture;
    }

    @Override
    public Identifier getLastTexture() {
        return this.lastTexture;
    }
}
