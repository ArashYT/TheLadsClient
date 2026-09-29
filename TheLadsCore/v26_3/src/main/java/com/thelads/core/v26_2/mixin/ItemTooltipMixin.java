package com.thelads.core.v26_2.mixin;

import java.util.List;
import java.util.function.Consumer;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.NativeTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public class ItemTooltipMixin {
    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getTooltipLines", at = @At("RETURN"), require = 1)
    private List<Component> lads$details(List<Component> original, Item.TooltipContext context, Player player, TooltipFlag flag) {
        return NativeTooltips.append((ItemStack) (Object) this, original, flag);
    }

    @Inject(method = "addAttributeTooltips", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$attributes(Consumer<Component> sink, TooltipDisplay display, Player player, CallbackInfo callback) {
        if (NativeQualityOfLife.enabled("EnhancedToolbars")
            && !NativeQualityOfLife.bool("EnhancedToolbars", "Show Item Attributes", true)) callback.cancel();
    }
}
