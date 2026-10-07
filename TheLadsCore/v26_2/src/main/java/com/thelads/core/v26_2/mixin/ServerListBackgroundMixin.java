package com.thelads.core.v26_2.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the dark background dirt/stone texture behind the server entries on the multiplayer screen.
 */
@Mixin(AbstractSelectionList.class)
public abstract class ServerListBackgroundMixin {
    @Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
    private void ladsSuppressServerListBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if ((Object) this instanceof ServerSelectionList) {
            ci.cancel();
        }
    }
}
