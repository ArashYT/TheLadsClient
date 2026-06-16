/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.Sets
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.client.resources.sounds.Sound
 *  net.minecraft.client.resources.sounds.SoundInstance
 *  net.minecraft.client.resources.sounds.TickableSoundInstance
 *  net.minecraft.client.sounds.SoundEngine
 *  net.minecraft.client.sounds.SoundEngine$PlayResult
 *  net.minecraft.client.sounds.SoundEngineExecutor
 *  net.minecraft.client.sounds.SoundEventListener
 *  net.minecraft.client.sounds.SoundManager
 *  net.minecraft.client.sounds.WeighedSoundEvents
 *  net.minecraft.sounds.SoundSource
 *  org.slf4j.Logger
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.thelads.core.mixin.alwayson.raisesoundlimit;

import com.google.common.collect.Sets;
import com.thelads.core.features.alwayson.raisesoundlimit.common.SoundManagerDuck;
import com.thelads.core.features.alwayson.raisesoundlimit.common.SoundSystemDuck;
import com.thelads.core.mixin.alwayson.raisesoundlimit.access.ISoundExecutor;
import com.thelads.core.mixin.alwayson.raisesoundlimit.access.ISoundSystem;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={SoundManager.class})
public abstract class MixinSoundManager
implements SoundManagerDuck {
    @Unique
    private static final Set<Identifier> rsls$unknownSounds = Sets.newConcurrentHashSet();
    @Shadow
    @Final
    private SoundEngine soundEngine;
    @Mutable
    @Shadow
    @Final
    private Map<Identifier, WeighedSoundEvents> registry;
    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    public abstract void tick(boolean var1);

    @Inject(method={"<init>"}, at={@At(value="RETURN")}, remap=false)
    private void onInit(CallbackInfo ci) {
        this.registry = Collections.synchronizedMap(this.registry);
    }

    @WrapMethod(method={"queueTickingSound"})
    private void onPlayNextTick(TickableSoundInstance sound, Operation<Void> original) {
        if (this.rsls$shouldRunOffthread()) {
            ((ISoundSystem)this.soundEngine).getTaskQueue().execute(() -> original.call(new Object[]{sound}));
        } else {
            original.call(new Object[]{sound});
        }
    }

    @WrapMethod(method={"playDelayed"})
    private void onPlay(SoundInstance sound, int delay, Operation<Void> original) {
        if (this.rsls$shouldRunOffthread()) {
            ((ISoundSystem)this.soundEngine).getTaskQueue().execute(() -> original.call(new Object[]{sound, delay}));
        } else {
            original.call(new Object[]{sound, delay});
        }
    }

    @Inject(method={"play"}, at={@At(value="HEAD")}, cancellable=true)
    private void onPlay(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (this.rsls$shouldRunOffthread()) {
            if (!sound.canPlaySound()) {
                cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
                return;
            }
            WeighedSoundEvents soundSet = sound.resolve((SoundManager)(Object)this);
            Identifier identifier = sound.getIdentifier();
            if (soundSet == null) {
                if (rsls$unknownSounds.add(identifier)) {
                    LOGGER.warn("Unable to play unknown soundEvent:  {}", (Object)identifier);
                }
                cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
                return;
            }
            Sound sound2 = sound.getSound();
            if (sound2 == SoundManager.INTENTIONALLY_EMPTY_SOUND || sound2 == SoundManager.EMPTY_SOUND) {
                cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
                return;
            }
            cir.setReturnValue(SoundEngine.PlayResult.STARTED);
            ((SoundSystemDuck)this.soundEngine).rsls$schedulePlay(sound);
            return;
        }
    }

    @WrapMethod(method={"pauseAllExcept"})
    private void onPauseAll(SoundSource[] categories, Operation<Void> original) {
        if (this.rsls$shouldRunOffthread()) {
            ((ISoundSystem)this.soundEngine).getTaskQueue().execute(() -> original.call(new Object[]{categories}));
        } else {
            original.call(new Object[]{categories});
        }
    }

    @WrapMethod(method={"destroy"})
    private void onClose(Operation<Void> original) {
        if (this.rsls$shouldRunOffthread()) {
            SoundEngineExecutor soundEngineExecutor = ((ISoundSystem)this.soundEngine).getTaskQueue();
            Operation<Void> operation = original;
            Objects.requireNonNull(operation);
            Operation<Void> operation2 = operation;
            soundEngineExecutor.execute(() -> operation2.call(new Object[0]));
        } else {
            original.call(new Object[0]);
        }
    }

    @WrapMethod(method={"tick"})
    private void onTick(boolean paused, Operation<Void> original) {
        if (this.rsls$shouldRunOffthread()) {
            ((ISoundSystem)this.soundEngine).getTaskQueue().execute(() -> this.tick(paused));
        } else {
            original.call(new Object[]{paused});
        }
    }

    @WrapMethod(method={"resume"})
    private void onResumeAll(Operation<Void> original) {
        if (this.rsls$shouldRunOffthread()) {
            SoundEngineExecutor soundEngineExecutor = ((ISoundSystem)this.soundEngine).getTaskQueue();
            Operation<Void> operation = original;
            Objects.requireNonNull(operation);
            Operation<Void> operation2 = operation;
            soundEngineExecutor.execute(() -> operation2.call(new Object[0]));
        } else {
            original.call(new Object[0]);
        }
    }

    @WrapMethod(method={"refreshCategoryVolume"})
    private void onUpdateSoundVolume(SoundSource category, Operation<Void> original) {
        if (this.rsls$shouldRunOffthread()) {
            ((ISoundSystem)this.soundEngine).getTaskQueue().execute(() -> original.call(new Object[]{category}));
        } else {
            original.call(new Object[]{category});
        }
    }

    @WrapMethod(method={"stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"})
    private void onStop(SoundInstance sound, Operation<Void> original) {
        if (this.rsls$shouldRunOffthread()) {
            ((ISoundSystem)this.soundEngine).getTaskQueue().execute(() -> original.call(new Object[]{sound}));
        } else {
            original.call(new Object[]{sound});
        }
    }

    @WrapMethod(method={"updateCategoryVolume"})
    private void onSetVolume(SoundSource category, float volume, Operation<Void> original) {
        if (this.rsls$shouldRunOffthread()) {
            ((ISoundSystem)this.soundEngine).getTaskQueue().execute(() -> original.call(new Object[]{category, Float.valueOf(volume)}));
        } else {
            original.call(new Object[]{category, Float.valueOf(volume)});
        }
    }

    @WrapMethod(method={"addListener"})
    private void onRegisterListener(SoundEventListener listener, Operation<Void> original) {
        if (this.rsls$shouldRunOffthread()) {
            ((ISoundSystem)this.soundEngine).getTaskQueue().execute(() -> original.call(new Object[]{listener}));
        } else {
            original.call(new Object[]{listener});
        }
    }

    @WrapMethod(method={"removeListener"})
    private void onUnregisterListener(SoundEventListener listener, Operation<Void> original) {
        if (this.rsls$shouldRunOffthread()) {
            ((ISoundSystem)this.soundEngine).getTaskQueue().execute(() -> original.call(new Object[]{listener}));
        } else {
            original.call(new Object[]{listener});
        }
    }

    @WrapMethod(method={"stop(Lnet/minecraft/resources/Identifier;Lnet/minecraft/sounds/SoundSource;)V"})
    private void onStopSounds(Identifier id, SoundSource soundCategory, Operation<Void> original) {
        if (this.rsls$shouldRunOffthread()) {
            ((ISoundSystem)this.soundEngine).getTaskQueue().execute(() -> original.call(new Object[]{id, soundCategory}));
        } else {
            original.call(new Object[]{id, soundCategory});
        }
    }

    @Override
    @Unique
    public boolean rsls$shouldRunOffthread() {
        SoundEngineExecutor executor = ((ISoundSystem)this.soundEngine).getTaskQueue();
        Thread thread = ((ISoundExecutor)executor).getThread();
        return Thread.currentThread() != thread;
    }
}

