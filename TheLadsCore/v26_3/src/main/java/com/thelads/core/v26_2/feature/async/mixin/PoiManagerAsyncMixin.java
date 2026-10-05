package com.thelads.core.v26_2.feature.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Pair;
import com.thelads.core.v26_2.feature.async.AsyncTicking;
import java.util.Collection;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Points of interest (beds, job sites, bells, nether portals) are one map per level that loads sections on demand, and a
 * claim is a check then a write: every query a worker makes runs whole under the lock, streams read before it is released.
 * Priority above Lithium's, which replaces some of these and adds the two lithium$ entry points villagers use instead.
 */
@Mixin(value = PoiManager.class, priority = 1500)
abstract class PoiManagerAsyncMixin {
    private static final String TYPES = "Ljava/util/function/Predicate;";
    private static final String OCCUPANCY = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;";

    @WrapMethod(method = "add")
    private PoiRecord lads$add(BlockPos pos, Holder<PoiType> type, Operation<PoiRecord> original) { return AsyncTicking.locked(original, pos, type); }

    @WrapMethod(method = "remove")
    private void lads$remove(BlockPos pos, Operation<Void> original) { AsyncTicking.locked(original, pos); }

    @WrapMethod(method = "getCountInRange")
    private long lads$count(Predicate<Holder<PoiType>> types, BlockPos center, int radius, PoiManager.Occupancy occupancy, Operation<Long> original) {
        return AsyncTicking.locked(original, types, center, radius, occupancy);
    }

    @WrapMethod(method = "existsAtPosition")
    private boolean lads$existsAt(ResourceKey<PoiType> type, BlockPos pos, Operation<Boolean> original) { return AsyncTicking.locked(original, type, pos); }

    @WrapMethod(method = "getInSquare")
    private Stream<PoiRecord> lads$inSquare(Predicate<Holder<PoiType>> types, BlockPos center, int radius, PoiManager.Occupancy occupancy, Operation<Stream<PoiRecord>> original) {
        return AsyncTicking.lockedStream(original, types, center, radius, occupancy);
    }

    @WrapMethod(method = "getInRange")
    private Stream<PoiRecord> lads$inRange(Predicate<Holder<PoiType>> types, BlockPos center, int radius, PoiManager.Occupancy occupancy, Operation<Stream<PoiRecord>> original) {
        return AsyncTicking.lockedStream(original, types, center, radius, occupancy);
    }

    @WrapMethod(method = "getInChunk")
    private Stream<PoiRecord> lads$inChunk(Predicate<Holder<PoiType>> types, ChunkPos chunk, PoiManager.Occupancy occupancy, Operation<Stream<PoiRecord>> original) {
        return AsyncTicking.lockedStream(original, types, chunk, occupancy);
    }

    @WrapMethod(method = "findAll")
    private Stream<BlockPos> lads$findAll(Predicate<Holder<PoiType>> types, Predicate<BlockPos> filter, BlockPos center, int radius, PoiManager.Occupancy occupancy, Operation<Stream<BlockPos>> original) {
        return AsyncTicking.lockedStream(original, types, filter, center, radius, occupancy);
    }

    @WrapMethod(method = "findAllWithType")
    private Stream<Pair<Holder<PoiType>, BlockPos>> lads$findAllWithType(Predicate<Holder<PoiType>> types, Predicate<BlockPos> filter, BlockPos center, int radius,
                                                                       PoiManager.Occupancy occupancy, Operation<Stream<Pair<Holder<PoiType>, BlockPos>>> original) {
        return AsyncTicking.lockedStream(original, types, filter, center, radius, occupancy);
    }

    @WrapMethod(method = "findAllClosestFirstWithType")
    private Stream<Pair<Holder<PoiType>, BlockPos>> lads$findAllClosest(Predicate<Holder<PoiType>> types, Predicate<BlockPos> filter, BlockPos center, int radius,
                                                                      PoiManager.Occupancy occupancy, Operation<Stream<Pair<Holder<PoiType>, BlockPos>>> original) {
        return AsyncTicking.lockedStream(original, types, filter, center, radius, occupancy);
    }

    @WrapMethod(method = "find")
    private Optional<BlockPos> lads$find(Predicate<Holder<PoiType>> types, Predicate<BlockPos> filter, BlockPos center, int radius, PoiManager.Occupancy occupancy, Operation<Optional<BlockPos>> original) {
        return AsyncTicking.locked(original, types, filter, center, radius, occupancy);
    }

    @WrapMethod(method = "findClosest(" + TYPES + "Lnet/minecraft/core/BlockPos;I" + OCCUPANCY + ")Ljava/util/Optional;")
    private Optional<BlockPos> lads$findClosest(Predicate<Holder<PoiType>> types, BlockPos center, int radius, PoiManager.Occupancy occupancy, Operation<Optional<BlockPos>> original) {
        return AsyncTicking.locked(original, types, center, radius, occupancy);
    }

    @WrapMethod(method = "findClosest(" + TYPES + TYPES + "Lnet/minecraft/core/BlockPos;I" + OCCUPANCY + ")Ljava/util/Optional;")
    private Optional<BlockPos> lads$findClosestFiltered(Predicate<Holder<PoiType>> types, Predicate<BlockPos> filter, BlockPos center, int radius,
                                                        PoiManager.Occupancy occupancy, Operation<Optional<BlockPos>> original) {
        return AsyncTicking.locked(original, types, filter, center, radius, occupancy);
    }

    @WrapMethod(method = "findClosestWithType")
    private Optional<Pair<Holder<PoiType>, BlockPos>> lads$findClosestWithType(Predicate<Holder<PoiType>> types, BlockPos center, int radius, PoiManager.Occupancy occupancy,
                                                                               Operation<Optional<Pair<Holder<PoiType>, BlockPos>>> original) {
        return AsyncTicking.locked(original, types, center, radius, occupancy);
    }

    @WrapMethod(method = "take")
    private Optional<BlockPos> lads$take(Predicate<Holder<PoiType>> types, BiPredicate<Holder<PoiType>, BlockPos> filter, BlockPos center, int radius, Operation<Optional<BlockPos>> original) {
        return AsyncTicking.locked(original, types, filter, center, radius);
    }

    @WrapMethod(method = "getRandom")
    private Optional<BlockPos> lads$random(Predicate<Holder<PoiType>> types, Predicate<BlockPos> filter, PoiManager.Occupancy occupancy, BlockPos center, int radius,
                                           RandomSource random, Operation<Optional<BlockPos>> original) {
        return AsyncTicking.locked(original, types, filter, occupancy, center, radius, random);
    }

    @WrapMethod(method = "release")
    private boolean lads$release(BlockPos pos, Operation<Boolean> original) { return AsyncTicking.locked(original, pos); }

    @WrapMethod(method = "exists")
    private boolean lads$exists(BlockPos pos, Predicate<Holder<PoiType>> types, Operation<Boolean> original) { return AsyncTicking.locked(original, pos, types); }

    @WrapMethod(method = "getType")
    private Optional<Holder<PoiType>> lads$type(BlockPos pos, Operation<Optional<Holder<PoiType>>> original) { return AsyncTicking.locked(original, pos); }

    @WrapMethod(method = "sectionsToVillage")
    private int lads$sectionsToVillage(SectionPos pos, Operation<Integer> original) { return AsyncTicking.locked(original, pos); }

    @WrapMethod(method = "lithium$takeAt", require = 0)
    private Optional<BlockPos> lads$lithiumTake(Predicate<Holder<PoiType>> types, BiPredicate<Holder<PoiType>, BlockPos> filter, BlockPos pos, Operation<Optional<BlockPos>> original) {
        return AsyncTicking.locked(original, types, filter, pos);
    }

    @WrapMethod(method = "lithium$getNClosestFirstWithType", require = 0)
    private Collection<Pair<Holder<PoiType>, BlockPos>> lads$lithiumClosest(Predicate<Holder<PoiType>> types, Predicate<BlockPos> filter, BlockPos center, int radius,
                                                                          PoiManager.Occupancy occupancy, long limit, Operation<Collection<Pair<Holder<PoiType>, BlockPos>>> original) {
        return AsyncTicking.locked(original, types, filter, center, radius, occupancy, limit);
    }

    @WrapMethod(method = "ensureLoadedAndValid")
    private void lads$ensureLoaded(LevelReader reader, BlockPos center, int radius, Operation<Void> original) { AsyncTicking.locked(original, reader, center, radius); }
}
