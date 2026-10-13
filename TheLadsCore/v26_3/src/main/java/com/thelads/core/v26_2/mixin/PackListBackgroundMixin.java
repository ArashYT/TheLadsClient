package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.pack.PackMenuSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class PackListBackgroundMixin {
    @Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
    private void ladsSuppressPackListBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if ((Object) this instanceof TransferableSelectionList && PackMenuSettings.isTransparent()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true)
    private void ladsSuppressPackListSeparators(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if ((Object) this instanceof TransferableSelectionList && PackMenuSettings.isTransparent()) {
            ci.cancel();
        }
    }
}
