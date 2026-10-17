package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.SurvivalTweaks189;
import java.util.Random;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** SurvivalTweaks Stable block drops: a block's drops spawn at its centre (26.x: no random popResource offset); EntityItemMixin stills them. */
@Mixin(Block.class)
public abstract class SurvivalBlockDropsMixin {
    @Redirect(method = "spawnAsEntity", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextFloat()F"), require = 3)
    private static float ladsCentredDrop(Random random) {
        return SurvivalTweaks189.stableDrops() ? 0.5F : random.nextFloat();
    }
}
