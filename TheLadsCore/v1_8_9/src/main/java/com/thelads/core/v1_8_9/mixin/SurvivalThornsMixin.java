package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.SurvivalTweaks189;
import net.minecraft.enchantment.EnchantmentThorns;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** SurvivalTweaks No Thorns damage: Thorns armour loses no durability for hitting back (3) or for every hit taken (1). 1.8.9 has no Soul Speed. */
@Mixin(EnchantmentThorns.class)
public abstract class SurvivalThornsMixin {
    @Redirect(method = "onUserHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;damageItem(ILnet/minecraft/entity/EntityLivingBase;)V"), require = 2)
    private void ladsNoThornsDamage(ItemStack stack, int amount, EntityLivingBase user) {
        if (!SurvivalTweaks189.noThornsDamage()) stack.damageItem(amount, user);
    }
}
