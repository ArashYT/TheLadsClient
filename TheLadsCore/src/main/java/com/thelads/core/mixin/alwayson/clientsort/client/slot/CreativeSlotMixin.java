/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.inventory.Slot
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 */
package com.thelads.core.mixin.alwayson.clientsort.client.slot;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.util.inject.ISlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets={"net/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper"})
public abstract class CreativeSlotMixin
implements ISlot {
    @Shadow
    @Final
    private Slot target;

    @Override
    public int clientsort$getIndexInContainer() {
        return ((ISlot)this.target).clientsort$getIndexInContainer();
    }

    @Override
    public int clientsort$getIndexInMenu() {
        return ((ISlot)this.target).clientsort$getIndexInMenu();
    }
}
