package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractRecipeBookScreen.class)
public interface AbstractRecipeBookScreenAccessor {
    @Accessor("recipeBook")
    void setRecipeBook(net.minecraft.client.recipebook.RecipeBook recipeBook);
}
