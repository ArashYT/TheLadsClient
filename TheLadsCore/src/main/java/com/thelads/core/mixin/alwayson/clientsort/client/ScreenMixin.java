/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.clientsort.client;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.gui.TriggerButtonManager;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Screen.class})
public abstract class ScreenMixin {
    @Inject(method={"init(II)V"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/gui/screens/Screen;init()V", shift=At.Shift.AFTER)})
    private void afterInit(CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() instanceof CreativeModeInventoryScreen) {
            return;
        }
        this.clientsort$afterInit();
    }

    @Inject(method={"rebuildWidgets"}, at={@At(value="RETURN")})
    private void afterRebuildWidgets(CallbackInfo ci) {
        this.clientsort$afterInit();
    }

    @Unique
    private void clientsort$afterInit() {
        TriggerButtonManager.getContainerButtons().forEach(b -> ((ScreenAccessor)((Object)this)).clientsort$removeWidget((GuiEventListener)b));
        TriggerButtonManager.getPlayerButtons().forEach(b -> ((ScreenAccessor)((Object)this)).clientsort$removeWidget((GuiEventListener)b));
        TriggerButtonManager.afterScreenInit((Screen)((Object)this));
    }
}
