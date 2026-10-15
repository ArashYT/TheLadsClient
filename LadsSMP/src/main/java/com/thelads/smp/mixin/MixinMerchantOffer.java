package com.thelads.smp.mixin;

import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pathway 8: Prevents Villagers, Wandering Traders, and Trading Posts from trading disabled items.
 */
@Mixin(MerchantOffer.class)
public abstract class MixinMerchantOffer {
    @Shadow
    @Final
    private ItemStack result;

    @Inject(method = "isOutOfStock", at = @At("HEAD"), cancellable = true)
    private void ladssmp$markDisabledTradeOutOfStock(CallbackInfoReturnable<Boolean> cir) {
        if (ItemDisablerSystem.isDisabled(this.result)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "assemble", at = @At("HEAD"), cancellable = true)
    private void ladssmp$blockDisabledTradeAssemble(CallbackInfoReturnable<ItemStack> cir) {
        if (ItemDisablerSystem.isDisabled(this.result)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
