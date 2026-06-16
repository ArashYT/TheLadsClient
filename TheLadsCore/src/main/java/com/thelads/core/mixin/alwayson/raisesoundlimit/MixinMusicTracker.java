/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.toasts.ToastManager
 *  net.minecraft.client.sounds.MusicManager
 *  net.minecraft.client.sounds.SoundEngine
 *  net.minecraft.client.sounds.SoundEngineExecutor
 *  net.minecraft.client.sounds.SoundManager
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Coerce
 */
package com.thelads.core.mixin.alwayson.raisesoundlimit;

import com.thelads.core.mixin.alwayson.raisesoundlimit.access.ISoundManager;
import com.thelads.core.mixin.alwayson.raisesoundlimit.access.ISoundSystem;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(value={MusicManager.class})
public class MixinMusicTracker {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Unique
    private CompletableFuture<Void> rsls$playFuture;

    @WrapMethod(method={"startPlaying"})
    private void wrapPlay(@Coerce Object instance, Operation<Void> original) {
        CompletableFuture<Void> rsls$playFuture1 = this.rsls$playFuture;
        if (rsls$playFuture1 != null && !rsls$playFuture1.isDone()) {
            return;
        }
        SoundManager soundManager = this.minecraft.getSoundManager();
        SoundEngine soundSystem = ((ISoundManager)soundManager).getSoundSystem();
        SoundEngineExecutor taskQueue = ((ISoundSystem)soundSystem).getTaskQueue();
        this.rsls$playFuture = CompletableFuture.runAsync(() -> original.call(new Object[]{instance}), (Executor)taskQueue).orTimeout(15L, TimeUnit.SECONDS);
    }

    @WrapOperation(method={"startPlaying"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/gui/components/toasts/ToastManager;showNowPlayingToast()V")})
    private void wrapPlayListener(ToastManager instance, Operation<Void> original) {
        this.minecraft.execute(() -> original.call(new Object[]{instance}));
    }

    @WrapMethod(method={"showNowPlayingToastIfNeeded"})
    private void wrapTryShowToast(Operation<Void> original) {
        if (this.minecraft.isSameThread()) {
            original.call(new Object[0]);
        } else {
            Operation<Void> operation = original;
            Objects.requireNonNull(operation);
            Operation<Void> operation2 = operation;
            this.minecraft.execute(() -> operation2.call(new Object[0]));
        }
    }
}

