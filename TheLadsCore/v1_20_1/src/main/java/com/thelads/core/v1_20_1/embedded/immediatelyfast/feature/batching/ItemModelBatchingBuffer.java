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
// Modified by The Lads: repackaged into Lads Core (com.thelads.core.v1_20_1.embedded.immediatelyfast).
package com.thelads.core.v1_20_1.embedded.immediatelyfast.feature.batching;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.*;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;

public class ItemModelBatchingBuffer extends BatchingBuffer {

    private final Object2ObjectMap<ReferenceObjectPair<RenderType, LightingState>, RenderType> lightingRenderLayers = new Object2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<RenderType, ReferenceSet<RenderType>> renderLayerMap = new Reference2ObjectOpenHashMap<>();

    public ItemModelBatchingBuffer() {
        super(BatchingBuffers.createLayerBuffers(Minecraft.getInstance().renderBuffers().bufferSource().fixedBuffers.keySet().stream()
                .filter(layer -> layer.name.contains("glint"))
                .toArray(RenderType[]::new)));
    }

    @Override
    public VertexConsumer getBuffer(final RenderType layer) {
        if (this.fixedBuffers.containsKey(layer)) {
            return super.getBuffer(layer);
        }

        final LightingState lightingState = LightingState.current();
        final RenderType newLayer = this.lightingRenderLayers.computeIfAbsent(new ReferenceObjectImmutablePair<>(layer, lightingState), key -> new BatchingRenderLayers.WrappedRenderLayer(layer, lightingState::saveAndApply, lightingState::revert));
        this.renderLayerMap.computeIfAbsent(layer, key -> new ReferenceOpenHashSet<>()).add(newLayer);
        return super.getBuffer(newLayer);
    }

    @Override
    public void endBatch(final RenderType layer) {
        final Set<RenderType> renderLayers = this.renderLayerMap.remove(layer);
        if (renderLayers != null) {
            for (RenderType renderLayer : renderLayers) {
                super.endBatch(renderLayer);
            }
        } else {
            super.endBatch(layer);
        }
    }

    @Override
    public void endBatch() {
        RenderSystem.enableBlend();
        super.endBatch();
        RenderSystem.disableBlend();
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
