package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v26_2.feature.survivaltweaks.NativeSurvivalTweaks;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Tool.class)
public abstract class SurvivalFasterObsidianMixin {
    @ModifyExpressionValue(
            method = "getMiningSpeed",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;get()Ljava/lang/Object;")
    )
    private Object lads$boostObsidianMiningSpeed(Object original, @Local(argsOnly = true) BlockState state) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks.active() && mod != null && mod.isFasterObsidianMiningEnabled()
                && original instanceof Float speed
                && (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.RESPAWN_ANCHOR))) {
            return speed * 1.6f;
        }
        return original;
    }
}
