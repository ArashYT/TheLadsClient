/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler
 *  net.minecraft.util.RandomSource
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.raisesoundlimit;

import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={BiomeAmbientSoundsHandler.class})
public class MixinBiomeEffectSoundPlayer {
    @Mutable
    @Shadow
    @Final
    private RandomSource random;

    @Inject(method={"<init>"}, at={@At(value="RETURN")}, remap=false)
    private void replaceRandom(CallbackInfo ci) {
        this.random = RandomSource.create();
    }
}

