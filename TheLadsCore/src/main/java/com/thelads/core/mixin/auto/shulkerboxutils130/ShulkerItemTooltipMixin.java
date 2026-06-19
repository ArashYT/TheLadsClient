package com.thelads.core.mixin.auto.shulkerboxutils130;

import java.util.Optional;

import com.thelads.core.features.auto.shulkerboxutils.ShulkerBoxTooltipData;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces the vanilla shulker box tooltip with the graphical contents grid. */
@Mixin(Item.class)
public class ShulkerItemTooltipMixin {

    @Inject(method = "getTooltipImage(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true, require = 0)
    private void thelads$addContentsPreview(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents != null) {
                contents.copyInto(items);
            }
            cir.setReturnValue(Optional.of(new ShulkerBoxTooltipData(items)));
        }
    }
}
