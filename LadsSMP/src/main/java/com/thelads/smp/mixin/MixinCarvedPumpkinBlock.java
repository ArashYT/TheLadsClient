package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.features.ConsolidatedFeatures;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Consolidates 'golem_spawn_fix': Allows Iron Golems to spawn even when grass, flowers, snow, or carpets
 * are underneath the golem's arms, while preserving vanilla trySpawnGolem (so setPlayerCreated(true) is kept!).
 */
@Mixin(CarvedPumpkinBlock.class)
public abstract class MixinCarvedPumpkinBlock {
    @Shadow
    private BlockPattern ironGolemBase;

    @Shadow
    private BlockPattern ironGolemFull;

    @Shadow
    @Final
    private static Predicate<BlockState> PUMPKINS_PREDICATE;

    @Inject(method = "getOrCreateIronGolemBase", at = @At("HEAD"), cancellable = true)
    private void ladssmp$relaxedIronGolemBase(CallbackInfoReturnable<BlockPattern> cir) {
        if (!LadsConfig.get().features.golemSpawnFix) return;
        if (this.ironGolemBase == null) {
            this.ironGolemBase = BlockPatternBuilder.start()
                    .aisle("~ ~", "###", "~#~")
                    .where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK)))
                    .where('~', BlockInWorld.hasState(ConsolidatedFeatures::isPermittedGolemCornerBlock))
                    .build();
        }
        cir.setReturnValue(this.ironGolemBase);
    }

    @Inject(method = "getOrCreateIronGolemFull", at = @At("HEAD"), cancellable = true)
    private void ladssmp$relaxedIronGolemFull(CallbackInfoReturnable<BlockPattern> cir) {
        if (!LadsConfig.get().features.golemSpawnFix) return;
        if (this.ironGolemFull == null) {
            this.ironGolemFull = BlockPatternBuilder.start()
                    .aisle("~^~", "###", "~#~")
                    .where('^', BlockInWorld.hasState(PUMPKINS_PREDICATE))
                    .where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK)))
                    .where('~', BlockInWorld.hasState(ConsolidatedFeatures::isPermittedGolemCornerBlock))
                    .build();
        }
        cir.setReturnValue(this.ironGolemFull);
    }
}
