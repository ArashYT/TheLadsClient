/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.CreativeModeTab$ItemDisplayParameters
 *  net.minecraft.world.item.CreativeModeTabs
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package com.thelads.core.mixin.alwayson.clientsort.client.accessor;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={CreativeModeTabs.class})
public interface CreativeModeTabsAccessor {
    @Accessor(value="CACHED_PARAMETERS")
    public static void clientsort$setCachedParameters(CreativeModeTab.ItemDisplayParameters newParams) {
        throw new AssertionError();
    }
}
