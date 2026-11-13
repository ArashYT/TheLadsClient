package com.thelads.smp.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smart Backpacks' Backpacker Camp sets its trader's offers while the camp's chunk generates: it calls getOffers(), which first
 * builds the vanilla wandering-trader trades (server random, explorer-map structure search) on the worldgen thread, then clears
 * them. With C2ME that threw and the chunk never loaded; without it the map search waited on the main thread. Both froze the server.
 * An empty offer list up front makes getOffers() skip the vanilla trades; the camp adds its own as before.
 */
@Pseudo
@Mixin(targets = "com.teamsmartstreamlabs.smartbackpacks.worldgen.CampTraderOffers")
public abstract class MixinSmartBackpacksCampTrader {
    @Inject(method = "apply", at = @At("HEAD"), require = 0)
    private static void ladssmp$skipVanillaTrades(WanderingTrader trader, RandomSource random, CallbackInfo ci) {
        AbstractVillagerOffersAccessor offers = (AbstractVillagerOffersAccessor) trader;
        if (offers.ladssmp$getOffersField() == null) offers.ladssmp$setOffersField(new MerchantOffers());
    }
}
