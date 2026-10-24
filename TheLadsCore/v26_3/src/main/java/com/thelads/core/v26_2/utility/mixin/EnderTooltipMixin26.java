package com.thelads.core.v26_2.utility.mixin;

import com.thelads.core.client.EnderChestPeek;
import com.thelads.core.v26_2.feature.ShulkerTooltip;
import com.thelads.core.v26_2.utility.Utility26;
import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ender Chest Peek: an ender chest item's tooltip gets the 9x3 grid (ShulkerBoxUtils' ShulkerTooltip) of the last seen ender chest. */
@Mixin(ItemStack.class)
abstract class EnderTooltipMixin26 {
    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
    private void lads$enderChest(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        if (EnderChestPeek.on && Utility26.ender() != null && cir.getReturnValue().isEmpty() && ((ItemStack) (Object) this).is(Items.ENDER_CHEST))
            cir.setReturnValue(Optional.of(new ShulkerTooltip(Utility26.ender())));
    }
}
