package com.thelads.core.mixin.alwayson.skinlayers;

import com.thelads.core.features.alwayson.skinlayers.accessor.SkullSettings;
import com.thelads.core.features.alwayson.skinlayers.accessor.SkullBlockRenderStateAccessor;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SkullBlockRenderState.class)
public class SkullBlockRenderStateMixin implements SkullBlockRenderStateAccessor {
    @Unique
    private SkullSettings skinlayers$skullSettings = null;

    @Override
    public SkullSettings skinlayers$getSkullSettings() {
        return this.skinlayers$skullSettings;
    }

    @Override
    public void skinlayers$setSkullSettings(SkullSettings settings) {
        this.skinlayers$skullSettings = settings;
    }
}
