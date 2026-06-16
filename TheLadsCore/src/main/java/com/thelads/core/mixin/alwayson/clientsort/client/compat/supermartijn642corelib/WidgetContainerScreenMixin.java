/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Pseudo
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.clientsort.client.compat.supermartijn642corelib;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.gui.widget.TriggerButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets={"com.supermartijn642.core.gui.WidgetContainerScreen"})
public abstract class WidgetContainerScreenMixin
extends Screen {
    protected WidgetContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method={"extractRenderState"}, at={@At(value="RETURN")})
    private void afterRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.children().forEach(child -> {
            if (child instanceof TriggerButton) {
                TriggerButton button = (TriggerButton)((Object)child);
                button.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
        });
    }
}
