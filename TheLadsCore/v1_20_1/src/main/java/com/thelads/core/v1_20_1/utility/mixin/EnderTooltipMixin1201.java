package com.thelads.core.v1_20_1.utility.mixin;

import com.thelads.core.client.EnderChestPeek;
import com.thelads.core.v1_20_1.hud.items.ShulkerInventory1201;
import com.thelads.core.v1_20_1.utility.Utility1201;
import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ender Chest Peek: an ender chest item's tooltip gets the 9x3 grid (ShulkerBoxUtils' tooltip) of the last seen ender chest. */
@Mixin(ItemStack.class)
abstract class EnderTooltipMixin1201 {
    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
    private void lads$enderChest(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        if (EnderChestPeek.on && Utility1201.ender() != null && cir.getReturnValue().isEmpty() && ((ItemStack) (Object) this).is(Items.ENDER_CHEST))
            cir.setReturnValue(Optional.of(new ShulkerInventory1201.Tooltip(Utility1201.ender())));
    }
}
