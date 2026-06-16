/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.sounds.ChannelAccess
 *  net.minecraft.client.sounds.SoundEngine
 *  net.minecraft.client.sounds.SoundEngineExecutor
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package com.thelads.core.mixin.alwayson.raisesoundlimit.access;

import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={SoundEngine.class})
public interface ISoundSystem {
    @Accessor(value="channelAccess")
    public ChannelAccess getChannel();

    @Accessor(value="executor")
    public SoundEngineExecutor getTaskQueue();
}

