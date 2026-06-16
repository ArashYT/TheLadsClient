/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Display
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package com.thelads.core.mixin.alwayson.entityculling;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={Display.class})
public interface DisplayAccessor {
    @Invoker
    public void invokeSetWidth(float var1);

    @Invoker
    public void invokeSetHeight(float var1);
}

