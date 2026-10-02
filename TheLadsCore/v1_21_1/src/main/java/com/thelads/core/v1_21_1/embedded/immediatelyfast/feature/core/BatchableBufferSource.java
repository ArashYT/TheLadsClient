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
package com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.core;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMaps;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.resources.ResourceLocation;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.ImmediatelyFast;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.compat.IrisCompat;

import java.util.Arrays;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;

public class BatchableBufferSource extends MultiBufferSource.BufferSource implements AutoCloseable {

    /**
     * A fallback buffer has to be defined because Iris tries to release that buffer, so it can't be null. It should be fine
     * to reuse/release the buffer multiple times, as it won't ever be written into by minecraft or Iris.
     */
    private final static ByteBufferBuilder FALLBACK_BUFFER = new ByteBufferBuilder(0);

    protected final Map<RenderType, ReferenceSet<BufferBuilder>> pendingBuffers = IrisCompat.IRIS_LOADED ? new Object2ObjectLinkedOpenHashMap<>() : new Reference2ObjectLinkedOpenHashMap<>();
    protected final Set<RenderType> activeLayers = IrisCompat.IRIS_LOADED ? new ObjectLinkedOpenHashSet<>() : new ReferenceLinkedOpenHashSet<>();

    protected boolean drawFallbackLayersFirst = false;

    public BatchableBufferSource() {
        this(Object2ObjectSortedMaps.emptyMap());
    }

    public BatchableBufferSource(final SequencedMap<RenderType, ByteBufferBuilder> layerBuffers) {
        this(FALLBACK_BUFFER, layerBuffers);
    }

    public BatchableBufferSource(final ByteBufferBuilder fallbackBuffer, final SequencedMap<RenderType, ByteBufferBuilder> layerBuffers) {
        super(fallbackBuffer, layerBuffers);
    }

    @Override
    public VertexConsumer getBuffer(final RenderType layer) {
        if (!this.drawFallbackLayersFirst) {
            if (this.lastSharedType != null && this.lastSharedType != layer && !this.fixedBuffers.containsKey(this.lastSharedType)) {
                this.drawFallbackLayersFirst = true;
            }
        }

        if (IrisCompat.IRIS_LOADED) {
            IrisCompat.skipExtension.set(!IrisCompat.isRenderingLevel.getAsBoolean());
        }

        final BufferBuilder bufferBuilder;
        final boolean hasBufferForRenderLayer = layer.canConsolidateConsecutiveGeometry() && this.pendingBuffers.containsKey(layer);
        if (!layer.canConsolidateConsecutiveGeometry()) {
            bufferBuilder = new BufferBuilder(this.getNextBufferAllocator(), layer.mode(), layer.format());
            this.lastSharedType = layer;
        } else if (hasBufferForRenderLayer) {
            bufferBuilder = this.pendingBuffers.get(layer).iterator().next();
        } else if (this.fixedBuffers.containsKey(layer)) {
            bufferBuilder = new BufferBuilder(this.fixedBuffers.get(layer), layer.mode(), layer.format());
        } else {
            bufferBuilder = new BufferBuilder(this.getNextBufferAllocator(), layer.mode(), layer.format());
            this.lastSharedType = layer;
        }

        if (IrisCompat.IRIS_LOADED) {
            IrisCompat.skipExtension.set(false);
        }

        if (hasBufferForRenderLayer) {
            if ((ImmediatelyFast.config.debug_only_use_last_usage_for_batch_ordering || layer.name.contains("immediatelyfast:renderlast")) && this.activeLayers.contains(layer)) { // Fix for https://github.com/RaphiMC/ImmediatelyFast/issues/181
                this.activeLayers.remove(layer);
                this.activeLayers.add(layer);
            }
        } else {
            this.pendingBuffers.computeIfAbsent(layer, k -> new ReferenceLinkedOpenHashSet<>()).add(bufferBuilder);
            this.activeLayers.add(layer);
        }

        return bufferBuilder;
    }

    @Override
    public void endLastBatch() {
        this.lastSharedType = null;
        this.drawFallbackLayersFirst = false;

        int sortedLayersLength = 0;
        final RenderType[] sortedLayers = new RenderType[this.activeLayers.size()];
        for (RenderType layer : this.activeLayers) {
            if (!this.fixedBuffers.containsKey(layer)) {
                sortedLayers[sortedLayersLength++] = layer;
            }
        }
        if (sortedLayersLength == 0) {
            return;
        }

        Arrays.sort(sortedLayers, (l1, l2) -> Integer.compare(this.getLayerOrder(l1), this.getLayerOrder(l2)));
        for (int i = 0; i < sortedLayersLength; i++) {
            this.endBatch(sortedLayers[i]);
        }
    }

    @Override
    public void endBatch() {
        if (this.activeLayers.isEmpty()) {
            this.close();
            return;
        }

        this.endLastBatch();
        for (RenderType layer : this.fixedBuffers.keySet()) {
            this.endBatch(layer);
        }
    }

    @Override
    public void endBatch(final RenderType layer) {
        if (this.drawFallbackLayersFirst) {
            this.endLastBatch();
        }

        this.drawDirect(layer);
    }

    @Override
    public void close() {
        this.lastSharedType = null;
        this.drawFallbackLayersFirst = false;

        for (Set<BufferBuilder> buffers : this.pendingBuffers.values()) {
            for (BufferBuilder bufferBuilder : buffers) {
                final MeshData builtBuffer = bufferBuilder.build();
                if (builtBuffer != null) {
                    builtBuffer.close();
                }
                BufferAllocatorPool.returnBufferAllocatorSafe(bufferBuilder.buffer);
            }
        }

        this.activeLayers.clear();
        this.pendingBuffers.clear();
    }

    public void drawDirect(final RenderType layer) {
        if (IrisCompat.IRIS_LOADED && !IrisCompat.isRenderingLevel.getAsBoolean()) {
            IrisCompat.renderWithExtendedVertexFormat.accept(false);
        }

        this.activeLayers.remove(layer);
        final Set<BufferBuilder> buffers = this.pendingBuffers.remove(layer);
        if (buffers != null) {
            for (BufferBuilder bufferBuilder : buffers) {
                final ByteBufferBuilder prevBufferAllocator = this.sharedBuffer;
                this.sharedBuffer = bufferBuilder.buffer;
                this.endBatch(layer, bufferBuilder);
                this.sharedBuffer = prevBufferAllocator;
                BufferAllocatorPool.returnBufferAllocatorSafe(bufferBuilder.buffer);
            }
        }
        if (this.lastSharedType == layer) {
            this.lastSharedType = null;
        }

        if (IrisCompat.IRIS_LOADED && !IrisCompat.isRenderingLevel.getAsBoolean()) {
            IrisCompat.renderWithExtendedVertexFormat.accept(true);
        }
    }

    public boolean hasActiveLayers() {
        return !this.activeLayers.isEmpty();
    }

    protected int getLayerOrder(final RenderType layer) {
        if (layer == null) {
            return Integer.MAX_VALUE;
        }

        int order = 0;
        if (layer instanceof RenderType.CompositeRenderType multiPhase) {
            final ResourceLocation textureId = multiPhase.state().textureState.cutoutTexture().orElse(null);
            if (textureId != null) {
                if (textureId.getPath().startsWith("textures/entity/horse/")) {
                    final String horseTexturePath = textureId.getPath().substring("textures/entity/horse/".length());
                    if (horseTexturePath.startsWith("horse_markings")) {
                        return 2;
                    } else if (horseTexturePath.startsWith("armor/")) {
                        return 3;
                    } else {
                        return 1;
                    }
                } else if (textureId.toString().startsWith("minecraft:textures/entity/wolf/")) {
                    if (textureId.equals(WolfCollarLayer.WOLF_COLLAR_LOCATION)) {
                        return 2;
                    } else {
                        return 1;
                    }
                } else if (textureId.getPath().startsWith("textures/entity/villager/")) {
                    final String villagerTexturePath = textureId.getPath().substring("textures/entity/villager/".length());
                    if (villagerTexturePath.startsWith("type/")) {
                        return 2;
                    } else if (villagerTexturePath.startsWith("profession/")) {
                        return 3;
                    } else if (villagerTexturePath.startsWith("profession_level/")) {
                        return 4;
                    } else {
                        return 1;
                    }
                } else if (textureId.equals(Sheets.ARMOR_TRIMS_SHEET)) {
                    order = 1;
                } else if (layer.name.startsWith("text") || layer.name.startsWith("neoforge_text") || layer.name.startsWith("forge_text")) {
                    // Draws vanilla text over custom font layers
                    // Fixes https://github.com/RaphiMC/ImmediatelyFast/issues/81, https://github.com/RaphiMC/ImmediatelyFast/issues/287, https://github.com/RaphiMC/ImmediatelyFast/issues/288
                    if (textureId.getNamespace().equals("minecraft")) {
                        order = 2;
                    } else {
                        order = 1;
                    }
                } else if (textureId.getNamespace().equals("cataclysm")) { // https://github.com/RaphiMC/ImmediatelyFast/issues/371
                    // Appears to be fixed in 3.00+
                    if (textureId.getPath().equals("textures/entity/maledictus/phantom_halberd.png")) {
                        return 2;
                    } else if (textureId.getPath().equals("textures/entity/maledictus/phantom_halberd_discard.png")) {
                        return 1;
                    }
                }
            }
        }

        if (!layer.sortOnUpload()) {
            return order;
        } else {
            return 100_000_000 + order;
        }
    }

    private ByteBufferBuilder getNextBufferAllocator() {
        if (this.sharedBuffer != FALLBACK_BUFFER && this.lastSharedType == null && this.sharedBuffer.pointer != 0L) {
            return this.sharedBuffer;
        } else {
            return BufferAllocatorPool.borrowBufferAllocator();
        }
    }

}
