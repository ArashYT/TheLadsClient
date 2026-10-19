package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.showcase.ShowcaseDirector26;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Showcase director `hotbar off`: a widget take hides only the vanilla hotbar (and its item name). */
@Mixin(Hud.class)
public abstract class ShowcaseHotbarMixin {
    @Inject(method = {"extractItemHotbar", "extractSelectedItemName"}, at = @At("HEAD"), cancellable = true)
    private void lads$showcaseHotbar(CallbackInfo ci) {
        if (ShowcaseDirector26.hideHotbar) ci.cancel();
    }
}
