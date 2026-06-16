/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.protocol.game.VecDeltaCodec
 *  net.minecraft.world.phys.Vec3
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package com.thelads.core.mixin.alwayson.vmp.access;

import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={VecDeltaCodec.class})
public interface ITrackedPosition {
    @Accessor(value="base")
    public Vec3 getPos();
}

