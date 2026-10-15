package com.thelads.smp.mixin;

import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pathway 9: Prevents disabled items (or recipe-only disabled items) from appearing in CraftingMenu result slots.
 */
@Mixin(CraftingMenu.class)
public abstract class MixinCraftingMenu {

    @Inject(method = "slotChangedCraftingGrid", at = @At("RETURN"))
    private static void ladssmp$filterDisabledCraftingResult(
            AbstractContainerMenu menu,
            ServerLevel level,
            Player player,
            CraftingContainer craftSlots,
            ResultContainer resultSlots,
            RecipeHolder<CraftingRecipe> recipeHint,
            CallbackInfo ci) {
        if (resultSlots == null) return;
        ItemStack result = resultSlots.getItem(0);
        if (ItemDisablerSystem.isDisabledForRecipeOutput(result)) {
            resultSlots.setItem(0, ItemStack.EMPTY);
        }
    }
}
