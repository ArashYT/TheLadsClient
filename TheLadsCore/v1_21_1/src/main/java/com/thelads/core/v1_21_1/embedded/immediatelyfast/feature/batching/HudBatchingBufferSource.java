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

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceObjectImmutablePair;
import it.unimi.dsi.fastutil.objects.ReferenceObjectPair;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.client.renderer.RenderType;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.core.BatchableBufferSource;

import java.util.SequencedMap;
import java.util.Set;

public class HudBatchingBufferSource extends BatchableBufferSource {

    private final Object2ObjectMap<ReferenceObjectPair<RenderType, LightingState>, RenderType> lightingRenderLayers = new Object2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<RenderType, ReferenceSet<RenderType>> renderLayerMap = new Reference2ObjectOpenHashMap<>();
    private boolean renderingItem = false;
    private boolean renderingItemDecorations = false;
    private boolean currentlyDrawing = false;

    public HudBatchingBufferSource(final ByteBufferBuilder fallbackBuffer, final SequencedMap<RenderType, ByteBufferBuilder> layerBuffers) {
        super(fallbackBuffer, layerBuffers);
    }

    public void setRenderingItem(final boolean renderingItem) {
        this.renderingItem = renderingItem;
    }

    public void setRenderingItemDecorations(final boolean renderingItemDecorations) {
        this.renderingItemDecorations = renderingItemDecorations;
    }

    public boolean isCurrentlyDrawing() {
        return this.currentlyDrawing;
    }

    @Override
    public VertexConsumer getBuffer(final RenderType layer) {
        if (layer.name.contains("glint")) {
            return super.getBuffer(layer);
        } else if (this.renderingItem) {
            final LightingState lightingState = LightingState.current();
            final RenderType newLayer = this.lightingRenderLayers.computeIfAbsent(new ReferenceObjectImmutablePair<>(layer, lightingState), key -> new BatchingBuffers.WrappedRenderLayer(layer, lightingState::saveAndApply, lightingState::revert));
            this.renderLayerMap.computeIfAbsent(layer, key -> new ReferenceOpenHashSet<>()).add(newLayer);
            return super.getBuffer(newLayer);
        } else if (this.renderingItemDecorations) {
            if (layer == RenderType.guiOverlay()) {
                return super.getBuffer(RenderType.gui());
            }
        }
        return super.getBuffer(layer);
    }

    @Override
    public void drawDirect(final RenderType layer) {
        this.currentlyDrawing = true;
        try {
            final Set<RenderType> renderLayers = this.renderLayerMap.remove(layer);
            if (renderLayers != null) {
                for (RenderType renderLayer : renderLayers) {
                    super.drawDirect(renderLayer);
                }
            } else {
                super.drawDirect(layer);
            }
        } finally {
            this.currentlyDrawing = false;
        }
    }

    @Override
    public void endBatch() {
        super.endBatch();
        this.lightingRenderLayers.clear();
        this.renderLayerMap.clear();
    }

    @Override
    public void close() {
        super.close();
        this.lightingRenderLayers.clear();
        this.renderLayerMap.clear();
    }

}
