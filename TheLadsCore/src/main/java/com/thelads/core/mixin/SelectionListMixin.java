package com.thelads.core.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server list: drop the translucent list backdrop and the header/footer
 * separator strips so only the entries float over the panorama.
 */
@Mixin(AbstractSelectionList.class)
public class SelectionListMixin {

    @Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsNoListBackground(GuiGraphicsExtractor g, CallbackInfo ci) {
        if ((Object) this instanceof ServerSelectionList) ci.cancel();
    }

    @Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsNoListSeparators(GuiGraphicsExtractor g, CallbackInfo ci) {
        if ((Object) this instanceof ServerSelectionList) ci.cancel();
    }
}
