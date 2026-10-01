package com.thelads.core.v26_2.embedded.etf.mixin.mixins;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import com.thelads.core.v26_2.embedded.etf.utils.ETFRenderLayerWithTexture;

import java.util.Optional;

import net.minecraft.client.renderer.rendertype.RenderSetup;
@Pseudo
@Mixin(value = RenderType.class)
public abstract class MixinMultiPhase implements ETFRenderLayerWithTexture {

    @Shadow @Final private RenderSetup state;

    @Override
    public Optional<Identifier> etf$getId() {
        var optional = state.textures.values().stream().findFirst();
        return optional.map(it -> it.location());
    }
}

