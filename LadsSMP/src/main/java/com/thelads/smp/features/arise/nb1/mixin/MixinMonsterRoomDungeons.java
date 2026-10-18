package com.thelads.smp.features.arise.nb1.mixin;

import com.thelads.smp.features.arise.nb1.ExtraDungeonsFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.MonsterRoomFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin target: net.minecraft.world.level.levelgen.feature.MonsterRoomFeature
 * Method 1: public boolean place(net.minecraft.world.level.WorldGenLevel, net.minecraft.world.level.chunk.ChunkGenerator, net.minecraft.util.RandomSource, net.minecraft.core.BlockPos);
 * Method 2: private net.minecraft.world.entity.EntityType<?> randomEntityId(net.minecraft.util.RandomSource);
 */
@Mixin(MonsterRoomFeature.class)
public abstract class MixinMonsterRoomDungeons {

    @Unique
    private static final ThreadLocal<ExtraDungeonsFeature.DungeonTheme> ladssmp$currentTheme = new ThreadLocal<>();

    @Inject(method = "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"))
    private void ladssmp$beforePlaceDungeon(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ExtraDungeonsFeature.isEnabled()) {
            ladssmp$currentTheme.set(ExtraDungeonsFeature.selectThemeForPosition(level, pos, random));
        }
    }

    @Inject(method = "randomEntityId", at = @At("HEAD"), cancellable = true)
    private void ladssmp$themedSpawnerMob(RandomSource random, CallbackInfoReturnable<EntityType<?>> cir) {
        ExtraDungeonsFeature.DungeonTheme theme = ladssmp$currentTheme.get();
        if (theme != null) {
            cir.setReturnValue(theme.getEntityType());
        }
    }

    @ModifyArg(
        method = "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/feature/MonsterRoomFeature;safeSetBlock(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/function/Predicate;)V"),
        index = 2
    )
    private BlockState ladssmp$themedDungeonWall(BlockState original) {
        ExtraDungeonsFeature.DungeonTheme theme = ladssmp$currentTheme.get();
        if (theme != null) {
            if (original.is(Blocks.COBBLESTONE)) {
                return theme.getPrimaryWall();
            } else if (original.is(Blocks.MOSSY_COBBLESTONE)) {
                return theme.getSecondaryWall();
            }
        }
        return original;
    }

    @Inject(method = "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z", at = @At("RETURN"))
    private void ladssmp$afterPlaceDungeon(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ladssmp$currentTheme.remove();
    }
}
