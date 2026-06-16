/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.audio.SoundBuffer
 *  net.minecraft.client.sounds.SoundBufferLibrary
 *  net.minecraft.server.packs.resources.ResourceProvider
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.raisesoundlimit;

import com.mojang.blaze3d.audio.SoundBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={SoundBufferLibrary.class})
public class MixinSoundLoader {
    @Mutable
    @Shadow
    @Final
    private Map<Identifier, CompletableFuture<SoundBuffer>> cache;

    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void syncMap(ResourceProvider resourceFactory, CallbackInfo ci) {
        this.cache = Collections.synchronizedMap(this.cache);
    }
}

