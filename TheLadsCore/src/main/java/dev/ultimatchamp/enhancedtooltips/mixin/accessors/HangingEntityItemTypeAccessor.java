/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.decoration.HangingEntity
 *  net.minecraft.world.item.HangingEntityItem
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package dev.ultimatchamp.enhancedtooltips.mixin.accessors;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.HangingEntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={HangingEntityItem.class})
public interface HangingEntityItemTypeAccessor {
    @Accessor(value="type")
    public EntityType<? extends HangingEntity> get();
}

