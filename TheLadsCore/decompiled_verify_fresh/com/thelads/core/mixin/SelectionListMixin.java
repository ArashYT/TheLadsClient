/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.components.AbstractSelectionList
 *  net.minecraft.client.gui.screens.multiplayer.ServerSelectionList
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={AbstractSelectionList.class})
public class SelectionListMixin {
    @Inject(method={"extractListBackground"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void ladsNoListBackground(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (this instanceof ServerSelectionList) {
            ci.cancel();
        }
    }

    @Inject(method={"extractListSeparators"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void ladsNoListSeparators(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (this instanceof ServerSelectionList) {
            ci.cancel();
        }
    }
}

