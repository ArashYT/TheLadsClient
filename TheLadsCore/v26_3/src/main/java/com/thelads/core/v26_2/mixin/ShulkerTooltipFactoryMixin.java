package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.ShulkerTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientTooltipComponent.class)
public interface ShulkerTooltipFactoryMixin {
    @Inject(method = "create(Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;",
        at = @At("HEAD"), cancellable = true, require = 1)
    private static void lads$factory(TooltipComponent data, CallbackInfoReturnable<ClientTooltipComponent> callback) {
        if (data instanceof ShulkerTooltip preview) callback.setReturnValue(preview);
    }
}
