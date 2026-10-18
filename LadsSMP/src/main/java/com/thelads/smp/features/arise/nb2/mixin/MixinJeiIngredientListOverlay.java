package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.DistractionFreeRecipesManager;
import mezz.jei.gui.input.GuiTextFieldFilter;
import mezz.jei.gui.overlay.IngredientListOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to JEI's IngredientListOverlay to hide the ingredient panel when the search filter is blank
 * (Distraction Free Recipes).
 */
@Environment(EnvType.CLIENT)
@Mixin(value = IngredientListOverlay.class, remap = false)
public abstract class MixinJeiIngredientListOverlay {

    @Shadow
    private GuiTextFieldFilter searchField;

    @Inject(
            method = "isListDisplayed()Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onIsListDisplayed(CallbackInfoReturnable<Boolean> cir) {
        String filter = this.searchField != null ? this.searchField.getValue() : "";
        if (DistractionFreeRecipesManager.shouldHideOverlay(filter)) {
            cir.setReturnValue(false);
        }
    }
}
