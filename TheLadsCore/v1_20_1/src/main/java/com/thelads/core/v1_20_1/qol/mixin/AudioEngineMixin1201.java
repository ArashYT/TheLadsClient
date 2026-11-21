package com.thelads.core.v1_20_1.qol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.PredictionMatcher;
import com.thelads.core.client.audio.AudioEngine;
import com.thelads.core.client.audio.SoundPriority;
import com.thelads.core.client.audio.SourcePool;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Audio Engine hooks for 1.20.1. All policy lives in common (client.audio); this only reads Minecraft types and calls vanilla stop and
 * requestPreload. No OpenAL call is made here: eviction goes through SoundEngine.stop, which queues the release on the sound executor.
 */
@Mixin(SoundEngine.class)
public class AudioEngineMixin1201 {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow @Final private ChannelAccess channelAccess;
    @Shadow @Final private SoundManager soundManager;
    @Shadow private int tickCount;

    @Shadow public void stop(SoundInstance instance) { throw new IllegalStateException("shadow"); }
    @Shadow public String getDebugString() { throw new IllegalStateException("shadow"); }
    @Shadow public void requestPreload(Sound sound) { throw new IllegalStateException("shadow"); }
    @Shadow private float calculateVolume(float volume, SoundSource source) { throw new IllegalStateException("shadow"); }

    @Unique private final Map<SoundInstance, Integer> lads$start = new IdentityHashMap<>();
    @Unique private float lads$boost = 1f;

    @Unique private static boolean lads$ready() {
        if (!AudioEngine.isInited()) {
            FabricLoader l = FabricLoader.getInstance();
            AudioEngine.init(l.isModLoaded("audioengineplus"), l.isModLoaded("sound_physics_remastered"), "(1.20.1)",
                "decode LRU cache skipped (vanilla eviction not safe), mono resample skipped");
        }
        return AudioEngine.active();
    }

    @Unique private static double lads$dist(Vec3 eye, SoundInstance s) {
        return s.isRelative() || eye == null ? 0.0 : eye.distanceTo(new Vec3(s.getX(), s.getY(), s.getZ()));
    }

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F", ordinal = 0), cancellable = true)
    private void lads$audioPlay(SoundInstance inst, CallbackInfo ci) {
        lads$boost = 1f;
        if (!lads$ready()) return;
        Sound sound = inst.getSound();
        if (sound == null) return;
        LocalPlayer p = Minecraft.getInstance().player;
        Vec3 eye = p == null ? null : p.getEyePosition();
        ResourceLocation loc = inst.getLocation();
        String id = loc.toString(), path = loc.getPath(), cat = inst.getSource().name();
        boolean positional = !inst.isRelative() && inst.getAttenuation() != SoundInstance.Attenuation.NONE;
        boolean tickable = inst instanceof TickableSoundInstance;
        double dist = positional ? lads$dist(eye, inst) : 0.0;
        float vol = inst.getVolume();
        float gain = calculateVolume(vol, inst.getSource());
        float range = Math.max(vol, 1f) * sound.getAttenuationDistance();
        boolean predicted = PredictionMatcher.SHARED.peek(id, inst.getX(), inst.getY(), inst.getZ(), System.currentTimeMillis());
        float m = AudioEngine.onPlay(cat, id, path, positional, inst.isLooping(), tickable, predicted,
            inst.getX(), inst.getY(), inst.getZ(), dist, gain, range, tickCount);
        if (m <= 0f) { ci.cancel(); return; }
        lads$boost = m;
        if (predicted || sound.shouldStream() || !AudioEngine.module().pool.get()) { lads$start.put(inst, tickCount); return; }

        String usage = getDebugString();
        if (AudioEngine.needScan(usage, instanceToChannel.size())) {
            List<SourcePool.Candidate> list = new ArrayList<>(instanceToChannel.size());
            for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> e : instanceToChannel.entrySet()) {
                SoundInstance s = e.getKey();
                if (e.getValue().isStopped()) continue;
                Sound ss = s.getSound();
                double d = lads$dist(eye, s);
                Integer st = lads$start.get(s);
                list.add(new SourcePool.Candidate(s, SoundPriority.classify(s.getSource().name(), s.getLocation().getPath(), d), d * d,
                    st == null ? 0 : st, s.isLooping() || s instanceof TickableSoundInstance, ss != null && ss.shouldStream()));
            }
            SourcePool.Candidate v = AudioEngine.pickEviction(usage, list, SoundPriority.classify(cat, path, dist), dist * dist);
            if (v == AudioEngine.DROP_NEW) { ci.cancel(); return; }
            if (v != null) {
                stop((SoundInstance) v.key);
                channelAccess.scheduleTick();
            }
        }
        lads$start.put(inst, tickCount);
    }

    /** The merged-sound volume boost of the dedup rule, applied where vanilla computes the start volume. */
    @WrapOperation(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float lads$boostVolume(SoundEngine self, float v, SoundSource s, Operation<Float> original) {
        float r = original.call(self, v, s);
        float b = lads$boost;
        lads$boost = 1f;
        return b == 1f ? r : Math.min(1f, r * b);
    }

    /** Occlusion: the wall gain joins the per-tick volume vanilla already pushes to every playing source. */
    @WrapOperation(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"))
    private float lads$occludeVolume(SoundInstance sound, Operation<Float> original) {
        float v = original.call(sound);
        return AudioEngine.occlusionOn() ? v * AudioEngine.occlusion().gain(sound) : v;
    }

    @Inject(method = "tick(Z)V", at = @At("TAIL"))
    private void lads$audioTick(boolean paused, CallbackInfo ci2) {
        if (!AudioEngine.active()) return;
        AudioEngine.tickDebug(System.currentTimeMillis());
        if (paused) return;
        if (tickCount % 200 == 0) {
            lads$start.keySet().retainAll(instanceToChannel.keySet());
            AudioEngine.occlusion().prune(instanceToChannel);
        }
        if (!AudioEngine.occlusionOn()) {
            if (AudioEngine.occlusion().size() > 0) AudioEngine.occlusion().clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        if (level == null || p == null) return;
        Vec3 eye = p.getEyePosition();
        List<SoundInstance> near = new ArrayList<>();
        Map<SoundInstance, Double> dist = new IdentityHashMap<>();
        for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> e : instanceToChannel.entrySet()) {
            SoundInstance s = e.getKey();
            if (e.getValue().isStopped() || s.isRelative() || s.getAttenuation() == SoundInstance.Attenuation.NONE) continue;
            String cat = s.getSource().name();
            if (cat.equals("MUSIC") || cat.equals("RECORDS") || cat.equals("MASTER")) continue;
            double d = lads$dist(eye, s);
            if (d < 2.0) continue;
            near.add(s);
            dist.put(s, d);
        }
        near.sort((a, b) -> Double.compare(dist.get(a), dist.get(b)));
        List<Object> keys = new ArrayList<Object>(near);
        int used = AudioEngine.occlusion().update(tickCount, AudioEngine.module().occlusionTicks.getIntValue(),
            AudioEngine.module().rayBudget.getIntValue(), keys, (key, rays) -> {
                SoundInstance s = (SoundInstance) key;
                Vec3 to = new Vec3(s.getX(), s.getY(), s.getZ());
                Vec3 cur = eye;
                int blocks = 0;
                for (int r = 0; r < com.thelads.core.client.audio.Occlusion.MAX_RAYS_PER_SOUND
                    && blocks < com.thelads.core.client.audio.Occlusion.MAX_BLOCKS; r++) {
                    rays[0]++;
                    BlockHitResult hit = level.clip(new ClipContext(cur, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                    if (hit.getType() == HitResult.Type.MISS) break;
                    blocks++;
                    Vec3 at = hit.getLocation();
                    if (at.distanceTo(to) < 1.0) break;
                    cur = at.add(to.subtract(at).normalize()); // step 1 block past the wall, else a start inside a block hits again
                }
                return blocks;
            });
        AudioEngine.countRays(used);
    }

    /** After a resource reload: init line, then decode the short critical sounds off-thread through vanilla's own preload queue. */
    @Inject(method = "reload()V", at = @At("TAIL"))
    private void lads$audioReload(CallbackInfo ci2) {
        if (!lads$ready()) return;
        AudioEngine.clearCaches();
        lads$start.clear();
        if (!AudioEngine.module().decodeCache.get()) return;
        RandomSource rnd = RandomSource.create();
        for (String name : AudioEngine.PRELOAD) {
            WeighedSoundEvents ev = soundManager.getSoundEvent(new ResourceLocation(name));
            if (ev == null) continue;
            List<Sound> seen = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Sound s = ev.getSound(rnd);
                if (s == null || s == SoundManager.EMPTY_SOUND || s == SoundManager.INTENTIONALLY_EMPTY_SOUND || s.shouldStream() || seen.contains(s)) continue;
                seen.add(s);
                requestPreload(s);
            }
        }
    }
}
