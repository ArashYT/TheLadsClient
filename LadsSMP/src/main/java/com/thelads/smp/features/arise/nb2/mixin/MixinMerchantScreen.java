package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.TradeUsesManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to MerchantScreen to render remaining trade uses badge directly on trade arrow buttons (Trade Uses).
 */
@Environment(EnvType.CLIENT)
@Mixin(MerchantScreen.class)
public abstract class MixinMerchantScreen extends AbstractContainerScreen<MerchantMenu> {

    public MixinMerchantScreen(MerchantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(
            method = "extractButtonArrows(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/item/trading/MerchantOffer;II)V",
            at = @At("TAIL")
    )
    private void onExtractButtonArrows(GuiGraphicsExtractor graphics, MerchantOffer offer, int x, int y, CallbackInfo ci) {
        if (offer != null) {
            TradeUsesManager.renderTradeBadge(graphics, this.font, offer.getUses(), offer.getMaxUses(), x, y);
        }
    }
}
