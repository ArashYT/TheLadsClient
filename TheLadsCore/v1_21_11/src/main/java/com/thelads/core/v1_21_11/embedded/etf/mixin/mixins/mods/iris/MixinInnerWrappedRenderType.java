package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.mods.iris;
import net.irisshaders.iris.layer.InnerWrappedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFRenderLayerWithTexture;

import java.util.Optional;


/**
 * Required in case Iris wraps an instance of {@link ETFRenderLayerWithTexture}
 * <p>
 * This is assumed to be required, whereas I know {@link MixinOuterWrappedRenderType} is required.
 */
@Pseudo
@Mixin(value = InnerWrappedRenderType.class)
public abstract class MixinInnerWrappedRenderType implements ETFRenderLayerWithTexture {


    @Shadow
    public abstract RenderType unwrap();

    @Override
    public Optional<Identifier> etf$getId() {
        if (unwrap() instanceof ETFRenderLayerWithTexture etf)
            return etf.etf$getId();
        return Optional.empty();
    }
}
