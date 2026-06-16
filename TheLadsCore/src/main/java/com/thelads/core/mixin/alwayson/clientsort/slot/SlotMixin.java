/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.inventory.Slot
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 */
package com.thelads.core.mixin.alwayson.clientsort.slot;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.util.inject.ISlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value={Slot.class})
public abstract class SlotMixin
implements ISlot {
    @Shadow
    public int index;

    @Shadow
    public abstract int getContainerSlot();

    @Override
    public int clientsort$getIndexInContainer() {
        return this.getContainerSlot();
    }

    @Override
    public int clientsort$getIndexInMenu() {
        return this.index;
    }
}
