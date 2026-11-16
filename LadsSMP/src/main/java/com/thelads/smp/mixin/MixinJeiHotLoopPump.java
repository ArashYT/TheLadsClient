package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** JEI registers 14,000+ recipes and indexes 23,000 ingredients in one go; the per-recipe and per-ingredient steps are pump points. */
@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.collect.RecipeIngredientRoleMap", remap = false)
public class MixinJeiHotLoopPump {
    @Inject(method = "addRecipe", at = @At("HEAD"), remap = false, require = 0)
    private void ladssmp$pump(CallbackInfo ci) {
        LoadingPump.poll();
    }
}
