/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.components.AbstractScrollArea
 *  net.minecraft.client.gui.components.AbstractSelectionList
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.smoothscrolling;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={AbstractSelectionList.class})
public class EntryListWidgetMixin {
    @Inject(method={"extractWidgetRenderState"}, at={@At(value="HEAD")})
    private void manipulateScrollAmount(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ((com.thelads.core.features.alwayson.smoothscrolling.ScrollableWidgetManipulator)(Object)this).smoothScrollingRefurbished$manipulateScrollAmount(delta);
    }
}

