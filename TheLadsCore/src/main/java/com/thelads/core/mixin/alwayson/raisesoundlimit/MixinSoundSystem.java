/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.ModifyReturnValue
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  com.llamalad7.mixinextras.sugar.Share
 *  com.llamalad7.mixinextras.sugar.ref.LocalRef
 *  it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.resources.sounds.SoundInstance
 *  net.minecraft.client.resources.sounds.TickableSoundInstance
 *  net.minecraft.client.sounds.ChannelAccess
 *  net.minecraft.client.sounds.ChannelAccess$ChannelHandle
 *  net.minecraft.client.sounds.SoundEngine
 *  net.minecraft.client.sounds.SoundEngineExecutor
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.raisesoundlimit;

import com.thelads.core.features.alwayson.raisesoundlimit.common.ListFromSortedSet;
import com.thelads.core.features.alwayson.raisesoundlimit.common.SoundSystemDuck;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={SoundEngine.class})
public abstract class MixinSoundSystem
implements SoundSystemDuck {
    @Mutable
    @Shadow
    @Final
    private Map<SoundInstance, Integer> soundDeleteTime;
    @Mutable
    @Shadow
    @Final
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow
    @Final
    private List<TickableSoundInstance> queuedTickableSounds;
    @Mutable
    @Shadow
    @Final
    private List<TickableSoundInstance> tickingSounds;
    @Shadow
    @Final
    private SoundEngineExecutor executor;
    @Shadow
    private boolean loaded;
    @Unique
    private AtomicLong rsls$droppedSoundsPerf;
    @Unique
    private Set<SoundInstance> rsls$pendingSounds;
    @Unique
    private ArrayList<TickableSoundInstance> rsls$cachedTickableSoundInstanceList;

    @Shadow
    public abstract void playDelayed(SoundInstance var1, int var2);
    @Shadow
    public abstract SoundEngine.PlayResult play(SoundInstance var1);

    @Inject(method={"<init>"}, at={@At(value="RETURN")}, remap=false)
    private void onInit(CallbackInfo ci) {
        this.soundDeleteTime = Collections.synchronizedMap(this.soundDeleteTime);
        this.instanceToChannel = Collections.synchronizedMap(this.instanceToChannel);
        this.tickingSounds = new ListFromSortedSet<TickableSoundInstance>((SortedSet<TickableSoundInstance>)new ObjectLinkedOpenHashSet(this.tickingSounds));
        this.rsls$droppedSoundsPerf = new AtomicLong();
        this.rsls$pendingSounds = Collections.synchronizedSet(new HashSet());
    }

    @Redirect(method={"tick"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/sounds/ChannelAccess;scheduleTick()V"))
    private void dontTickChannel(ChannelAccess instance) {
        this.executor.execute(instance::scheduleTick);
    }

    @Redirect(method={"tickInGameSound"}, at=@At(value="INVOKE", target="Ljava/util/List;stream()Ljava/util/stream/Stream;"))
    private Stream<?> optimizeNextTickIteration(List<?> instance) {
        if (instance == this.queuedTickableSounds) {
            for (TickableSoundInstance soundInstance : this.queuedTickableSounds) {
                this.rsls$schedulePlay((SoundInstance)soundInstance);
            }
            return null;
        }
        return instance.stream();
    }

    @Override
    public void rsls$schedulePlay(SoundInstance instance) {
        long scheduleTime = System.nanoTime();
        this.rsls$pendingSounds.add(instance);
        this.executor.execute(() -> {
            if (!this.loaded) {
                return;
            }
            this.rsls$pendingSounds.remove(instance);
            if (System.nanoTime() - scheduleTime < 1000000000L) {
                this.rsls$playInternal0(instance);
            } else {
                this.rsls$droppedSoundsPerf.incrementAndGet();
            }
        });
    }

    @Override
    public void rsls$playInternal0(SoundInstance instance) {
        this.play(instance);
    }

    @Redirect(method={"tickInGameSound"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/sounds/SoundEngine;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;"))
    private SoundEngine.PlayResult redirectDelayedPlay(SoundEngine instance, SoundInstance sound) {
        this.rsls$schedulePlay(sound);
        return SoundEngine.PlayResult.STARTED;
    }

    @Inject(method={"reload"}, at={@At(value="RETURN")})
    private void onReload(CallbackInfo ci) {
        this.rsls$droppedSoundsPerf.set(0L);
    }

    @ModifyReturnValue(method={"getChannelDebugString"}, at={@At(value="RETURN")})
    private String appendDebugString(String original) {
        long dropped = this.rsls$droppedSoundsPerf.get();
        if (dropped != 0L) {
            return original + String.format(" (%d dropped)", dropped);
        }
        return original;
    }

    @Redirect(method={"tickInGameSound"}, at=@At(value="INVOKE", target="Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"))
    private <T> Stream<T> tickDisableFilter(Stream<T> instance, Predicate<? super T> predicate) {
        if (instance == null) {
            return null;
        }
        return instance.filter(predicate);
    }

    @Redirect(method={"tickInGameSound"}, at=@At(value="INVOKE", target="Ljava/util/stream/Stream;forEach(Ljava/util/function/Consumer;)V"))
    private <T> void tickDisableForEach(Stream<T> instance, Consumer<? super T> consumer) {
        if (instance == null) {
            return;
        }
        instance.forEach(consumer);
    }

    @ModifyReturnValue(method={"isActive"}, at={@At(value="RETURN")})
    private boolean modifyIsPlaying(boolean original, SoundInstance sound) {
        return original || this.rsls$pendingSounds.contains(sound);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"tickInGameSound"})
    private void wrapTick(Operation<Void> original, @Share(value="rsls$pendingTicks") LocalRef<List<TickableSoundInstance>> rsls$pendingTicks) {
        ArrayList<TickableSoundInstance> list = this.rsls$cachedTickableSoundInstanceList;
        if (list == null) {
            list = new ArrayList<>();
            this.rsls$cachedTickableSoundInstanceList = list;
        }
        rsls$pendingTicks.set(list);
        try {
            original.call(new Object[0]);
        }
        finally {
            if (!list.isEmpty()) {
                Minecraft client = Minecraft.getInstance();
                if (client != null) {
                    TickableSoundInstance[] array = (TickableSoundInstance[])list.toArray(TickableSoundInstance[]::new);
                    client.execute(() -> {
                        for (TickableSoundInstance instance : array) {
                            instance.tick();
                        }
                    });
                }
                list.clear();
            }
        }
    }

    @Redirect(method={"tickInGameSound"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/resources/sounds/TickableSoundInstance;tick()V"))
    private void redirectTickSound(TickableSoundInstance instance, @Share(value="rsls$pendingTicks") LocalRef<List<TickableSoundInstance>> rsls$pendingTicks) {
        ((List)rsls$pendingTicks.get()).add(instance);
    }

    @Override
    public SoundEngineExecutor rsls$getExecutor() {
        return this.executor;
    }
}

