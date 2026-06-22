package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.rendertype.RenderType;
import java.util.SequencedMap;

public interface MultiBufferSource {
    VertexConsumer getBuffer(RenderType renderType);

    static BufferSource immediateWithBuffers(SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers, ByteBufferBuilder sharedBuffer) {
        return new BufferSource(sharedBuffer, fixedBuffers);
    }

    public static class BufferSource implements MultiBufferSource, AutoCloseable {
        protected ByteBufferBuilder sharedBuffer;
        protected final SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers;
        protected RenderType lastSharedType;

        protected BufferSource(ByteBufferBuilder sharedBuffer, SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers) {
            this.sharedBuffer = sharedBuffer;
            this.fixedBuffers = fixedBuffers;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return null;
        }

        public void endBatch() {}
        public void endBatch(RenderType renderType) {}
        public void endBatch(RenderType renderType, BufferBuilder bufferBuilder) {}
        public void endLastBatch() {}

        @Override
        public void close() {}
    }
}
