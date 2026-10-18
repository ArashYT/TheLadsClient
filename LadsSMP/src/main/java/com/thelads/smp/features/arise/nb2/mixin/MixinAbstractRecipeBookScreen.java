package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.NotEnoughRecipeBookManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to AbstractRecipeBookScreen to intercept recipe book toggle button
 * and toggle JEI overlay visibility instead (Not Enough Recipe Book).
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractRecipeBookScreen.class)
public abstract class MixinAbstractRecipeBookScreen {

    @Inject(
            method = "initButton()V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onInitButton(CallbackInfo ci) {
        if (NotEnoughRecipeBookManager.isDisabledMode()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "onRecipeBookButtonClick()V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRecipeBookButtonClick(CallbackInfo ci) {
        if (NotEnoughRecipeBookManager.isToggleMode()) {
            NotEnoughRecipeBookManager.toggleJeiOverlay();
            ci.cancel();
        }
    }
}
