package com.thelads.core.v1_21_1.embedded.etf.compat;



import net.minecraft.client.renderer.RenderType;
import org.apache.logging.log4j.util.TriConsumer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFUtils2;
import com.thelads.core.v1_21_1.embedded.etf.utils.URenderTypeToVertexConsumer;


/**
 * A separate class to handle a specific sodium injection case for sodium 0.5.6+
 * This is called in one place and handles usage of sodium classes in a quarantined way.
 */
public abstract class SodiumGetBufferInjector {

    private static final TriConsumer<URenderTypeToVertexConsumer, RenderType, VertexConsumer> INSTANCE = get();


    public static void inject(URenderTypeToVertexConsumer provider, RenderType renderLayer, VertexConsumer vertexConsumer) {
        if (INSTANCE != null) INSTANCE.accept(provider, renderLayer, vertexConsumer);
    }

    private static TriConsumer<URenderTypeToVertexConsumer, RenderType, VertexConsumer> get() {
        return null;
    }
}
