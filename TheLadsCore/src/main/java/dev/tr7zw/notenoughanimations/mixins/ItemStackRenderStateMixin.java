/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  net.minecraft.client.renderer.item.ItemStackRenderState
 *  net.minecraft.world.item.ItemStack
 *  org.spongepowered.asm.mixin.Mixin
 */
package dev.tr7zw.notenoughanimations.mixins;

import dev.tr7zw.notenoughanimations.access.ExtendedItemStackRenderState;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value={ItemStackRenderState.class})
public class ItemStackRenderStateMixin
implements ExtendedItemStackRenderState {
    private ItemStack itemStack = null;

    @Override
    public ItemStack getItemStack() {
        return this.itemStack;
    }

    @Override
    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }
}

