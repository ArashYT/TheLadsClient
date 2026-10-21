package com.thelads.core.v1_8_9.mixin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import paulscode.sound.SoundSystem;

/**
 * Fixes MC-35714 / MC-35856: In vanilla 1.8.9, closing any GUI screen (Minecraft.displayGuiScreen(null))
 * calls SoundHandler.resumeSounds() unconditionally, which calls SoundManager.resumeAllSounds().
 *
 * <p>Vanilla 1.8.9's resumeAllSounds() iterated over all keys in playingSounds and invoked
 * sndSystem.play(channel), restarting sounds that had already finished playing (paulscode's
 * Library.play() on a stopped source replays it from the beginning).
 *
 * <p>This mixin mirrors the 1.9+ fix: tracking which channels were actually playing and paused
 * by pauseAllSounds() in {@code lads$pausedChannels}, resuming only those channels, and clearing
 * the list so subsequent GUI closures never replay stale sounds.
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerResumeMixin189 {
    @Shadow @Final private Map<ISound, String> invPlayingSounds;

    private final List<String> lads$pausedChannels = new ArrayList<String>();

    @Inject(method = "pauseAllSounds", at = @At("HEAD"))
    private void lads$onPauseStart(CallbackInfo ci) {
        this.lads$pausedChannels.clear();
    }

    /** Only pause and track channels that are currently playing (skip finished/stopped ones). */
    @Redirect(method = "pauseAllSounds", at = @At(value = "INVOKE",
        target = "Lpaulscode/sound/SoundSystem;pause(Ljava/lang/String;)V", remap = false))
    private void lads$pauseIfPlaying(SoundSystem sndSystem, String channel) {
        if (sndSystem != null && sndSystem.playing(channel)) {
            sndSystem.pause(channel);
            this.lads$pausedChannels.add(channel);
        }
    }

    /** In resumeAllSounds, iterate ONLY over channels that were actually paused by pauseAllSounds. */
    @Redirect(method = "resumeAllSounds", at = @At(value = "INVOKE",
        target = "Ljava/util/Map;keySet()Ljava/util/Set;"))
    private Set<String> lads$onlyPausedChannels(Map<String, ISound> map) {
        Set<String> set = new HashSet<String>(this.lads$pausedChannels);
        this.lads$pausedChannels.clear();
        return set;
    }

    @Inject(method = "stopSound", at = @At("HEAD"))
    private void lads$onStopSound(ISound sound, CallbackInfo ci) {
        String channel = this.invPlayingSounds.get(sound);
        if (channel != null) {
            this.lads$pausedChannels.remove(channel);
        }
    }

    @Inject(method = "stopAllSounds", at = @At("HEAD"))
    private void lads$onStopAllSounds(CallbackInfo ci) {
        this.lads$pausedChannels.clear();
    }

    @Inject(method = "unloadSoundSystem", at = @At("HEAD"))
    private void lads$onUnloadSoundSystem(CallbackInfo ci) {
        this.lads$pausedChannels.clear();
    }
}
