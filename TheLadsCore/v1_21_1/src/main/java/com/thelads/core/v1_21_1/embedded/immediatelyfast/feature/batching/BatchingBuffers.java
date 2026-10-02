/*
 * This file is part of ImmediatelyFast - https://github.com/RaphiMC/ImmediatelyFast
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
// Modified by The Lads: repackaged into Lads Core (com.thelads.core.v1_21_1.embedded.immediatelyfast).
package com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.batching;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.SequencedMap;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public class BatchingBuffers {

    private static MultiBufferSource.BufferSource nonBatchingEntityVertexConsumers;
    private static HudBatchingBufferSource hudBatchingVertexConsumers;
    private static boolean isHudBatching;

    public static MultiBufferSource.BufferSource getNonBatchingEntityVertexConsumers() {
        if (nonBatchingEntityVertexConsumers == null) {
            final SequencedMap<RenderType, ByteBufferBuilder> layerBuffers = createLayerBuffers(Minecraft.getInstance().renderBuffers().bufferSource().fixedBuffers.keySet());
            nonBatchingEntityVertexConsumers = new MultiBufferSource.BufferSource(new ByteBufferBuilder(786432), layerBuffers);
        }
        return nonBatchingEntityVertexConsumers;
    }

    public static MultiBufferSource.BufferSource getHudBatchingVertexConsumers() {
        if (hudBatchingVertexConsumers == null) {
            final SequencedMap<RenderType, ByteBufferBuilder> layerBuffers = createLayerBuffers(Minecraft.getInstance().renderBuffers().bufferSource().fixedBuffers.keySet());
            hudBatchingVertexConsumers = new HudBatchingBufferSource(new ByteBufferBuilder(786432), layerBuffers);
        }
        return hudBatchingVertexConsumers;
    }

    public static void runBatched(final GuiGraphics drawContext, final Runnable runnable) {
        drawContext.flush();
        final MultiBufferSource.BufferSource prev = drawContext.bufferSource;
        drawContext.bufferSource = getHudBatchingVertexConsumers();
        isHudBatching = true;
        try {
            runnable.run();
            drawContext.flush();
        } finally {
            drawContext.bufferSource = prev;
            isHudBatching = false;
        }
    }

    public static MultiBufferSource.BufferSource beginHudBatching(final GuiGraphics drawContext) {
        drawContext.flush();
        final MultiBufferSource.BufferSource prev = drawContext.bufferSource;
        drawContext.bufferSource = getHudBatchingVertexConsumers();
        isHudBatching = true;
        return prev;
    }

    public static void endHudBatching(final GuiGraphics drawContext, final MultiBufferSource.BufferSource prev) {
        drawContext.flush();
        drawContext.bufferSource = prev;
        isHudBatching = false;
    }

    public static boolean isHudBatching() {
        return isHudBatching;
    }

    public static void tryForceDrawHudBuffers() {
        if (!hudBatchingVertexConsumers.isCurrentlyDrawing() && hudBatchingVertexConsumers.hasActiveLayers()) {
            final RenderSystemState renderSystemState = RenderSystemState.current();
            try {
                RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                hudBatchingVertexConsumers.endBatch();
            } finally {
                renderSystemState.apply();
            }
        }
    }

    private static SequencedMap<RenderType, ByteBufferBuilder> createLayerBuffers(final Set<RenderType> layers) {
        final SequencedMap<RenderType, ByteBufferBuilder> layerBuffers = new Object2ObjectLinkedOpenHashMap<>(layers.size());
        for (RenderType layer : layers) {
            layerBuffers.put(layer, new ByteBufferBuilder(layer.bufferSize()));
        }
        return layerBuffers;
    }

    public static class WrappedRenderLayer extends RenderType {

        public WrappedRenderLayer(final RenderType renderLayer, final Runnable additionalStartAction, final Runnable additionalEndAction) {
            super(renderLayer.name, renderLayer.format(), renderLayer.mode(), renderLayer.bufferSize(), renderLayer.affectsCrumbling(), renderLayer.sortOnUpload(), () -> {
                renderLayer.setupRenderState();
                additionalStartAction.run();
            }, () -> {
                renderLayer.clearRenderState();
                additionalEndAction.run();
            });
        }

    }

}
