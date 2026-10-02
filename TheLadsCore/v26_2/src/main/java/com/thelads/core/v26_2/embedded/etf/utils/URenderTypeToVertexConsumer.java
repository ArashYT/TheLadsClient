package com.thelads.core.v26_2.embedded.etf.utils;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

public class URenderTypeToVertexConsumer {

    public final net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer featureRenderer;
    
    public URenderTypeToVertexConsumer(net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer featureRenderer) {
        this.featureRenderer = featureRenderer;
    }
    
    public VertexConsumer getBuffer(RenderType type) {
        return featureRenderer.getVertexBuilder(type);
    }
    

}
