/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
 *  com.llamalad7.mixinextras.sugar.Share
 *  com.llamalad7.mixinextras.sugar.ref.LocalIntRef
 *  com.mojang.blaze3d.audio.Library
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Constant
 *  org.spongepowered.asm.mixin.injection.ModifyArg
 *  org.spongepowered.asm.mixin.injection.ModifyConstant
 *  org.spongepowered.asm.mixin.injection.Slice
 */
package com.thelads.core.mixin.alwayson.raisesoundlimit;

import com.thelads.core.features.alwayson.raisesoundlimit.RSLSConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.blaze3d.audio.Library;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value={Library.class})
public class MixinSoundEngine {
    @WrapOperation(method={"init"}, at={@At(value="INVOKE", target="Lcom/mojang/blaze3d/audio/Library;getChannelCount()I")})
    private int modifyMaxSourceFromConfig(Library instance, Operation<Integer> operation, @Share(value="rsls$actualSourcesCount") LocalIntRef actualSourcesCount) {
        int min = Math.min((Integer)operation.call(new Object[]{instance}), RSLSConfig.maxSourcesCount);
        actualSourcesCount.set(min);
        return min;
    }

    @ModifyArg(method={"init"}, at=@At(value="INVOKE", target="Lcom/mojang/blaze3d/audio/Library$CountingChannelPool;<init>(I)V", ordinal=0), slice=@Slice(from=@At(value="INVOKE", target="Lcom/mojang/blaze3d/audio/Library;getChannelCount()I")))
    private int modifyStaticSources(int maxSourceCount, @Share(value="rsls$actualSourcesCount") LocalIntRef actualSourcesCount, @Share(value="rsls$actualStaticSourcesCount") LocalIntRef actualStaticSourcesCount) {
        int min = actualSourcesCount.get() - RSLSConfig.maxStreamingSources;
        actualStaticSourcesCount.set(min);
        return min;
    }

    @ModifyArg(method={"init"}, at=@At(value="INVOKE", target="Lcom/mojang/blaze3d/audio/Library$CountingChannelPool;<init>(I)V", ordinal=1), slice=@Slice(from=@At(value="INVOKE", target="Lcom/mojang/blaze3d/audio/Library;getChannelCount()I")))
    private int modifyStreamingSources(int maxSourceCount, @Share(value="rsls$actualSourcesCount") LocalIntRef actualSourcesCount, @Share(value="rsls$actualStaticSourcesCount") LocalIntRef actualStaticSourcesCount) {
        return Math.min(actualSourcesCount.get(), actualSourcesCount.get() - actualStaticSourcesCount.get());
    }

    @ModifyConstant(method={"init"}, constant={@Constant(intValue=255)})
    private int modifyMaxSource(int constant) {
        if (constant == 255) {
            return Integer.MAX_VALUE;
        }
        return constant;
    }
}

