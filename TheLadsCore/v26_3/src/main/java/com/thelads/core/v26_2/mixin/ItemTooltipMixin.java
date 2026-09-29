package com.thelads.core.v26_2.mixin;

import java.util.List;
import java.util.function.Consumer;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.NativeTooltips;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public class ItemTooltipMixin {
    @Shadow @Final private static List<Component> OP_NBT_WARNING;

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "getTooltipLines")
    private List<Component> lads$details(Item.TooltipContext context, Player player, TooltipFlag flag, com.llamalad7.mixinextras.injector.wrapoperation.Operation<List<Component>> original) {
        ItemStack stack = (ItemStack) (Object) this;
        // Vanilla answers a hidden tooltip with an immutable list, and RETURN hooks of other mods that append to it (Tooltips TXF
        // adds durability and food lines) throw UnsupportedOperationException in the render thread. Answer exactly as vanilla
        // does without running them: a hidden tooltip shows nothing.
        if (!flag.isCreative() && stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).hideTooltip())
            return stack.getItem().shouldPrintOpWarning(stack, player) ? OP_NBT_WARNING : List.of();
        return NativeTooltips.append(stack, original.call(context, player, flag), flag);
    }

    @Inject(method = "addAttributeTooltips", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$attributes(Consumer<Component> sink, TooltipDisplay display, Player player, CallbackInfo callback) {
        if (NativeQualityOfLife.enabled("EnhancedToolbars")
            && !NativeQualityOfLife.bool("EnhancedToolbars", "Show Item Attributes", true)) callback.cancel();
    }
}
