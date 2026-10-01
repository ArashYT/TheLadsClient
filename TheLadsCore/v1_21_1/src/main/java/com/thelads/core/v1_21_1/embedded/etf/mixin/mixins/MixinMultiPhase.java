package com.thelads.core.v1_21_1.embedded.etf.mixin.mixins;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFRenderLayerWithTexture;

import java.util.Optional;

@Pseudo
@Mixin(value = RenderType.CompositeRenderType.class)
public abstract class MixinMultiPhase implements ETFRenderLayerWithTexture {


    @Shadow
    @Final
    public RenderType.CompositeState state;

    @Override
    public Optional<ResourceLocation> etf$getId() {
        return state.textureState.cutoutTexture();
    }
}
