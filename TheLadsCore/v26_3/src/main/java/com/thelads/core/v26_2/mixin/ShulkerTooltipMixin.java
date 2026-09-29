package com.thelads.core.v26_2.mixin;

import java.util.Optional;
import java.util.function.Consumer;
import com.thelads.core.v26_2.feature.ShulkerInventory;
import com.thelads.core.v26_2.feature.ShulkerSummary;
import com.thelads.core.v26_2.feature.ShulkerTooltip;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ShulkerTooltipMixin {
    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true, require = 1)
    private void lads$preview(CallbackInfoReturnable<Optional<TooltipComponent>> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (ShulkerInventory.preview(stack) && callback.getReturnValue().isEmpty())
            callback.setReturnValue(Optional.of(new ShulkerTooltip(ShulkerSummary.of(stack.get(DataComponents.CONTAINER)))));
    }

    @Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true, require = 1)
    private <T extends TooltipProvider> void lads$replaceList(DataComponentType<T> type, TooltipProvider.Getter<T> getter, Item.TooltipContext context,
        TooltipDisplay display, Consumer<Component> sink, TooltipFlag flag, CallbackInfo callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (type == DataComponents.CONTAINER && ShulkerInventory.preview(stack)
            && stack.getTooltipImage().orElse(null) instanceof ShulkerTooltip) callback.cancel();
    }
}
