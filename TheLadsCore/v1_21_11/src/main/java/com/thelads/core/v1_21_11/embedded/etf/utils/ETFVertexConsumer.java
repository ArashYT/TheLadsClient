package com.thelads.core.v1_21_11.embedded.etf.utils;

import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_11.embedded.etf.features.texture_handlers.ETFTexture;

/**
 * The interface Etf vertex consumer.
 * <p>
 * this turns any implemented vertex consumers into data holders to pass in etf context to assist entity feature rendering.
 * this has additional functionality for emf where the context may change through the iteration of each model part where
 * custom texture overrides exist.
 */
public interface ETFVertexConsumer {

    @Nullable
    ETFTexture etf$getETFTexture();

    @Nullable
    URenderTypeToVertexConsumer etf$getProvider();

    @Nullable
    RenderType etf$getRenderLayer();

    void etf$initETFVertexConsumer(URenderTypeToVertexConsumer provider, RenderType renderLayer);
}
