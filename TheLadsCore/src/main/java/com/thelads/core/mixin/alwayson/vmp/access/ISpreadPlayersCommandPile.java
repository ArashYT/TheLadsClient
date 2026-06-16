/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.commands.SpreadPlayersCommand$Position
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package com.thelads.core.mixin.alwayson.vmp.access;

import net.minecraft.server.commands.SpreadPlayersCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={SpreadPlayersCommand.Position.class})
public interface ISpreadPlayersCommandPile {
    @Accessor(value="x")
    public double getX();

    @Accessor(value="z")
    public double getZ();

    @Accessor(value="x")
    public void setX(double var1);

    @Accessor(value="z")
    public void setZ(double var1);

    @Invoker(value="dist")
    public double invokeGetDistance(SpreadPlayersCommand.Position var1);

    @Invoker(value="normalize")
    public void invokeNormalize();

    @Invoker(value="getLength")
    public double invokeAbsolute();
}

