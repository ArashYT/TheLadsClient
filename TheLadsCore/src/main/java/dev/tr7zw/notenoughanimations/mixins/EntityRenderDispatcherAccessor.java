/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.block.BlockModelResolver
 *  net.minecraft.client.renderer.entity.EntityRenderDispatcher
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package dev.tr7zw.notenoughanimations.mixins;

import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={EntityRenderDispatcher.class})
public interface EntityRenderDispatcherAccessor {
    @Accessor(value="blockModelResolver")
    public BlockModelResolver nea$getBlockModelResolver();
}

