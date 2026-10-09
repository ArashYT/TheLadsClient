package com.thelads.core.v1_20_1.hud.items.mixin;

import com.thelads.core.v1_20_1.hud.items.Food1201;
import com.thelads.core.v1_20_1.hud.items.ShulkerInventory1201;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lads' tooltip images draw themselves (vanilla's factory, also reached through Forge's tooltip gathering, knows only bundles). */
@Mixin(ClientTooltipComponent.class)
interface TooltipFactoryMixin {
    @Inject(method = "create(Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;",
        at = @At("HEAD"), cancellable = true)
    private static void lads$factory(TooltipComponent data, CallbackInfoReturnable<ClientTooltipComponent> callback) {
        if (data instanceof ShulkerInventory1201.Tooltip || data instanceof Food1201.FoodTooltip) callback.setReturnValue((ClientTooltipComponent) data);
    }
}
