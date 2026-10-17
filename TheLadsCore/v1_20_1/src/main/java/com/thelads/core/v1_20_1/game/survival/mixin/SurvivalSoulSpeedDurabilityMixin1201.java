package com.thelads.core.v1_20_1.game.survival.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v1_20_1.game.survival.NativeSurvivalTweaks1201;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** SurvivalTweaks: No Soul Speed damage. Walking on soul sand in Soul Speed boots no longer wears them down. */
@Mixin(LivingEntity.class)
public abstract class SurvivalSoulSpeedDurabilityMixin1201 {
    @WrapWithCondition(
            method = "tryAddSoulSpeed",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V")
    )
    private boolean lads$preventSoulSpeedDamage(ItemStack stack, int amount, LivingEntity entity, Consumer<LivingEntity> onBroken) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        return !(NativeSurvivalTweaks1201.active() && mod != null && mod.isNoEnchantDurabilityLossEnabled());
    }
}
