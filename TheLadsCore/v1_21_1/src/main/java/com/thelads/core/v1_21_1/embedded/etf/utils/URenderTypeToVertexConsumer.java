package com.thelads.core.v1_21_1.embedded.etf.utils;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;

public class URenderTypeToVertexConsumer {

    public final net.minecraft.client.renderer.MultiBufferSource delegate;

    public URenderTypeToVertexConsumer(net.minecraft.client.renderer.MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    public VertexConsumer getBuffer(RenderType type) {
        return delegate.getBuffer(type);
    }

}
