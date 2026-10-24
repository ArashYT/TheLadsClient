package com.thelads.core.v1_20_1.hud.items.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.hud.items.Food1201;
import com.thelads.core.v1_20_1.hud.items.ShulkerInventory1201;
import com.thelads.core.v1_20_1.hud.items.Tooltips1201;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item tooltips: Lads' lines after vanilla's (and after Forge's ItemTooltipEvent), the attribute block behind "Show Item
 * Attributes", and the tooltip image (shulker grid, else the AppleSkin food row) where the item has none of its own.
 */
@Mixin(ItemStack.class)
abstract class ItemTooltipMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void lads$lines(Player player, TooltipFlag flag, CallbackInfoReturnable<List<Component>> callback) {
        List<Component> lines = callback.getReturnValue(), result = Tooltips1201.append((ItemStack) (Object) this, lines, flag);
        if (result != lines) lines = result;
        if (com.thelads.core.config.ModuleManager.getInstance().getModule("NumericalEnchantments") != null && com.thelads.core.config.ModuleManager.getInstance().getModule("NumericalEnchantments").isEnabled()) {
            java.util.ArrayList<Component> converted = new java.util.ArrayList<>(lines.size());
            for (Component c : lines) {
                String str = c.getString();
                String arabic = com.thelads.core.client.RomanNumerals.convertTrailingRoman(str);
                if (!arabic.equals(str)) converted.add(Component.literal(arabic).withStyle(c.getStyle())); else converted.add(c);
            }
            callback.setReturnValue(converted);
        } else if (result != callback.getReturnValue()) {
            callback.setReturnValue(result);
        }
    }

    @WrapOperation(method = "getTooltipLines", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;shouldShowInTooltip(ILnet/minecraft/world/item/ItemStack$TooltipPart;)Z"))
    private boolean lads$attributes(int hideFlags, ItemStack.TooltipPart part, Operation<Boolean> original) {
        return original.call(hideFlags, part) && (part != ItemStack.TooltipPart.MODIFIERS || Tooltips1201.showAttributes());
    }

    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
    private void lads$image(CallbackInfoReturnable<Optional<TooltipComponent>> callback) {
        if (callback.getReturnValue().isPresent()) return;
        ItemStack stack = (ItemStack) (Object) this;
        if (ShulkerInventory1201.preview(stack))
            callback.setReturnValue(Optional.of(new ShulkerInventory1201.Tooltip(ShulkerInventory1201.contents(stack))));
        else {
            var food = Food1201.tooltip(stack);
            if (food != null) callback.setReturnValue(Optional.of(food));
        }
    }
}
