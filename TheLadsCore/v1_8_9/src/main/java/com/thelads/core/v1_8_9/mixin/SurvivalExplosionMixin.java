package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.SurvivalTweaks189;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.world.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** SurvivalTweaks 100% creeper block drops: a creeper's blast drops every block it breaks (vanilla: 1 / explosion size), as 26.x's TNT interaction. */
@Mixin(Explosion.class)
public abstract class SurvivalExplosionMixin {
    @Shadow @Final private Entity exploder;

    @ModifyArg(method = "doExplosionB", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;dropBlockAsItemWithChance(Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;FI)V"), index = 3, require = 1)
    private float ladsCreeperDropsAll(float chance) {
        return exploder instanceof EntityCreeper && SurvivalTweaks189.creeperFullDrops() ? 1.0F : chance;
    }
}
