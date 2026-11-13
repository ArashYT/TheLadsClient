package com.thelads.smp.mixin;

import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractVillager.class)
public interface AbstractVillagerOffersAccessor {
    @Accessor("offers")
    MerchantOffers ladssmp$getOffersField();

    @Accessor("offers")
    void ladssmp$setOffersField(MerchantOffers offers);
}
