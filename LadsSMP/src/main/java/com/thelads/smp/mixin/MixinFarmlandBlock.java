package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Consolidates 'nofeathertrample': Farmland is not trampled into dirt by any living entity
 * wearing Feather Falling boots or possessing the Slow Falling status effect.
 */
@Mixin(FarmlandBlock.class)
public abstract class MixinFarmlandBlock {

    @Inject(method = "turnToBaseBlock", at = @At("HEAD"), cancellable = true)
    private void ladssmp$noFeatherTrample(
            Entity entity,
            BlockState state,
            Level level,
            BlockPos pos,
            CallbackInfo ci) {
        if (!LadsConfig.get().features.noFeatherTrample) return;
        if (entity instanceof LivingEntity living) {
            Holder<Enchantment> featherFalling = level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.FEATHER_FALLING);
            if (EnchantmentHelper.getEnchantmentLevel(featherFalling, living) > 0 || living.hasEffect(MobEffects.SLOW_FALLING)) {
                ci.cancel();
            }
        }
    }
}
