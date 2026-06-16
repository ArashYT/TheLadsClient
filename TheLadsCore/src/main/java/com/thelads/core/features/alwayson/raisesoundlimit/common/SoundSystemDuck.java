/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.resources.sounds.SoundInstance
 */
package com.thelads.core.features.alwayson.raisesoundlimit.common;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngineExecutor;

public interface SoundSystemDuck {
    public void rsls$schedulePlay(SoundInstance var1);

    public void rsls$playInternal0(SoundInstance var1);

    public SoundEngineExecutor rsls$getExecutor();
}

