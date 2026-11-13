package com.thelads.smp.mixin;

import com.mojang.datafixers.util.Pair;
import com.thelads.smp.compat.MapSearchBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/** Stops an explorer-map structure search once its budget is spent (only inside a map loot roll; /locate is untouched). */
@Mixin(ChunkGenerator.class)
public abstract class MixinChunkGeneratorMapSearch {
    @Inject(method = "getStructureGeneratingAt", at = @At("HEAD"))
    private static void ladssmp$checkMapBudget(Set<Holder<Structure>> structures, LevelReader level, StructureManager manager, boolean skipKnown,
                                               StructurePlacement placement, ChunkPos chunk, CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        MapSearchBudget.check();
    }
}
