/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.item.CreativeModeTab
 *  net.minecraft.world.item.CreativeModeTab$Type
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.clientsort.client;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.gui.TriggerButtonManager;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={CreativeModeInventoryScreen.class})
public abstract class CreativeModeInventoryScreenMixin
extends Screen {
    protected CreativeModeInventoryScreenMixin(Component title) {
        super(title);
    }

    @Inject(method={"selectTab"}, at={@At(value="RETURN")})
    private void afterSelectTab(CreativeModeTab tab, CallbackInfo ci) {
        CreativeModeInventoryScreenMixin creativeModeInventoryScreenMixin = this;
        TriggerButtonManager.getPlayerButtons().forEach(x$0 -> creativeModeInventoryScreenMixin.removeWidget((GuiEventListener)x$0));
        if (tab.getType().equals((Object)CreativeModeTab.Type.INVENTORY)) {
            TriggerButtonManager.afterScreenInit(this);
        }
    }
}
