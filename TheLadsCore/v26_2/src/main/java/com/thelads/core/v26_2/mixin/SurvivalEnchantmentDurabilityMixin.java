package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v26_2.feature.survivaltweaks.NativeSurvivalTweaks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.effects.ChangeItemDamage;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Enchantment.class)
public abstract class SurvivalEnchantmentDurabilityMixin {
    @WrapWithCondition(
            method = "runLocationChangedEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/effects/EnchantmentLocationBasedEffect;onChangedBlock(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Z)V"
            )
    )
    private boolean lads$preventSoulSpeedAndThornsDamage(
            EnchantmentLocationBasedEffect effect,
            ServerLevel level,
            int enchantmentLevel,
            EnchantedItemInUse item,
            Entity entity,
            Vec3 pos,
            boolean applyTransientEffects
    ) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks.active() && mod != null && mod.isNoEnchantDurabilityLossEnabled() && effect instanceof ChangeItemDamage) {
            Identifier key = level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getKey((Enchantment) (Object) this);
            if (Enchantments.SOUL_SPEED.identifier().equals(key) || Enchantments.THORNS.identifier().equals(key)) {
                return false;
            }
        }
        return true;
    }
}
