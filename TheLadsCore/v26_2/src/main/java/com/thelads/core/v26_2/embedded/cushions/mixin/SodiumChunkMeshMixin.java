// Derived from Optimized Cushions Backport 1.0.0 (commit a6de9cc) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.thelads.core.v26_2.embedded.cushions.CushionBaker;
import com.thelads.core.v26_2.embedded.cushions.CushionSectionTasks;
import com.thelads.core.v26_2.embedded.cushions.CushionTracker;

@Pseudo
@Mixin(targets = {
    "net.caffeinemc.mods.sodium.fabric.level.FabricLevelRenderHooks",
    "net.caffeinemc.mods.sodium.neoforge.level.NeoForgeLevelRenderHooks"
}, remap = false)
public class SodiumChunkMeshMixin {
    private static final Logger optimizedcushions$LOGGER = LoggerFactory.getLogger("optimizedcushionsbackport");

    @Inject(method = "runChunkMeshAppenders", at = @At("HEAD"), remap = false, require = 0)
    private void optimizedcushions$bakeCushions(
        final List<?> renderers,
        final Function<ChunkSectionLayer, VertexConsumer> typeToConsumer,
        final @Coerce BlockAndTintGetter slice,
        final BlockPos origin,
        final CallbackInfo ci
    ) {
        Map<Integer, CushionTracker.Snapshot> cushions = CushionTracker.getForSection(SectionPos.asLong(origin));
        if (cushions == null || cushions.isEmpty()) {
            return;
        }

        final VertexConsumer buffer;
        try {
            buffer = typeToConsumer.apply(ChunkSectionLayer.CUTOUT);
        } catch (Exception e) {
            return;
        }
        if (buffer == null) {
            return;
        }
        SectionPos sectionPos = SectionPos.of(origin);
        // Snapshot copy: BY_SECTION inner maps are live ConcurrentHashMaps, so a worker
        // iterating values() directly could bake a torn membership set.
        for (CushionTracker.Snapshot cushion : new ArrayList<>(cushions.values())) {
            try {
                CushionBaker.emitFallback(buffer, cushion, sectionPos, slice);
            } catch (Exception e) {
                optimizedcushions$LOGGER.warn("Failed to bake cushion {}", cushion, e);
            }
        }

        CushionSectionTasks.addTask(SectionPos.asLong(origin), () -> CushionTracker.commitBakedSection(SectionPos.asLong(origin)));
    }
}
