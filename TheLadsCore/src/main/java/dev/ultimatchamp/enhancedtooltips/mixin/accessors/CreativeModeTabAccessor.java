/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.CreativeModeTab
 *  net.minecraft.world.item.CreativeModeTab$DisplayItemsGenerator
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package dev.ultimatchamp.enhancedtooltips.mixin.accessors;

import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={CreativeModeTab.class})
public interface CreativeModeTabAccessor {
    @Accessor
    public CreativeModeTab.DisplayItemsGenerator getDisplayItemsGenerator();
}

