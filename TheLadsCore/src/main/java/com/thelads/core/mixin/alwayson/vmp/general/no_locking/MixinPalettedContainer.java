/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.level.chunk.PalettedContainer
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Overwrite
 */
package com.thelads.core.mixin.alwayson.vmp.general.no_locking;

import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value={PalettedContainer.class})
public class MixinPalettedContainer {
    @Overwrite
    public void acquire() {
    }

    @Overwrite
    public void release() {
    }
}

