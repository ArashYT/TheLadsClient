/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.inventory.ContainerInput
 *  net.minecraft.world.inventory.Slot
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package com.thelads.core.mixin.alwayson.clientsort.client.accessor;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={AbstractContainerScreen.class})
public interface AbstractContainerScreenAccessor {
    @Accessor(value="leftPos")
    public int clientsort$getLeftPos();

    @Accessor(value="topPos")
    public int clientsort$getTopPos();

    @Accessor(value="imageWidth")
    public int clientsort$getImageWidth();

    @Accessor(value="playerInventoryTitle")
    public Component clientsort$getPlayerInventoryTitle();

    @Invoker(value="slotClicked")
    public void clientsort$slotClicked(Slot var1, int var2, int var3, ContainerInput var4);

    @Invoker(value="isHovering")
    public boolean clientsort$isHovering(Slot var1, double var2, double var4);
}
