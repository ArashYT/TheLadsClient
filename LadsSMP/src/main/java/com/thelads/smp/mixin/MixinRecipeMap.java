package com.thelads.smp.mixin;

import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

/**
 * Pathway 9: Filters RecipeMap#getRecipesFor across all RecipeTypes (crafting, smelting, blasting, smoking,
 * campfire, smithing, stonecutting, and modded machines) so disabled items cannot be crafted or processed.
 */
@Mixin(RecipeMap.class)
public abstract class MixinRecipeMap {

    @Inject(method = "getRecipesFor", at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void ladssmp$blockDisabledInputs(
            RecipeType<T> type, I input, Level level, CallbackInfoReturnable<Stream<RecipeHolder<T>>> cir) {
        if (input != null) {
            for (int i = 0; i < input.size(); i++) {
                ItemStack stack = input.getItem(i);
                if (ItemDisablerSystem.isDisabled(stack) || ItemDisablerSystem.isDisabledForRecipeOutput(stack)) {
                    cir.setReturnValue(Stream.empty());
                    return;
                }
            }
        }
    }

    @Inject(method = "getRecipesFor", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void ladssmp$filterDisabledRecipeOutputs(
            RecipeType<T> type, I input, Level level, CallbackInfoReturnable<Stream<RecipeHolder<T>>> cir) {
        Stream<RecipeHolder<T>> original = cir.getReturnValue();
        if (original != null) {
            cir.setReturnValue(original.filter(holder -> {
                if (holder == null) return false;
                String idStr = holder.id().identifier().toString();
                if (ItemDisablerSystem.isRecipeIdDisabled(idStr)) {
                    return false;
                }
                try {
                    ItemStack assembled = holder.value().assemble(input);
                    if (ItemDisablerSystem.isDisabledForRecipeOutput(assembled)) {
                        return false;
                    }
                } catch (Throwable ignored) {}
                return true;
            }));
        }
    }
}
