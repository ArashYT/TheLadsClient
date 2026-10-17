package com.thelads.core.v1_20_1.game.survival.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v1_20_1.game.survival.NativeSurvivalTweaks1201;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** SurvivalTweaks: Faster obsidian mining. A tool that mines obsidian, crying obsidian or a respawn anchor does it 1.6x as fast. */
@Mixin(DiggerItem.class)
public abstract class SurvivalFasterObsidianMixin1201 {
    @ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
    private float lads$boostObsidianMiningSpeed(float original, @Local(argsOnly = true) BlockState state) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (original > 1.0f && NativeSurvivalTweaks1201.active() && mod != null && mod.isFasterObsidianMiningEnabled()
                && (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.RESPAWN_ANCHOR))) {
            return original * 1.6f;
        }
        return original;
    }
}
