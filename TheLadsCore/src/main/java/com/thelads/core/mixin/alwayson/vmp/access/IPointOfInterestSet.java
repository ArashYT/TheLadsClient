/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.ai.village.poi.PoiSection
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package com.thelads.core.mixin.alwayson.vmp.access;

import net.minecraft.world.entity.ai.village.poi.PoiSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={PoiSection.class})
public interface IPointOfInterestSet {
    @Invoker(value="isValid")
    public boolean invokeIsValid();
}

