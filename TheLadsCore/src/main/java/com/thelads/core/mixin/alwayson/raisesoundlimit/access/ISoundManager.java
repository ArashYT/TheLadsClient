/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.sounds.SoundEngine
 *  net.minecraft.client.sounds.SoundManager
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package com.thelads.core.mixin.alwayson.raisesoundlimit.access;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={SoundManager.class})
public interface ISoundManager {
    @Accessor(value="soundEngine")
    public SoundEngine getSoundSystem();
}

