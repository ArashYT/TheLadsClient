package com.thelads.core.v1_8_9.mixin;

import java.util.HashSet;
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

/**
 * Fixes MC-35714 / MC-35856: closing any GUI (Minecraft.displayGuiScreen(null)) calls SoundHandler.resumeSounds() ->
 * SoundManager.resumeAllSounds(), which calls play() on every channel still listed in playingSounds. A sound that already
 * finished but was not yet removed by updateAllSounds() restarts from the beginning (paulscode play() on a stopped source).
 * Like 1.9+, only the channels pauseAllSounds() really paused are resumed. Both loops iterate playingSounds.keySet(), so the
 * keySet() calls are redirected: the play()/pause() calls go to SoundManager$SoundSystemStarterThread (package-private),
 * which a redirect could not name.
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerResumeMixin189 {
    @Shadow @Final private Map<String, ISound> playingSounds;
    @Shadow @Final private Map<ISound, String> invPlayingSounds;
    @Shadow public abstract boolean isSoundPlaying(ISound sound);

    private final Set<String> lads$pausedChannels = new HashSet<String>();

    /** pauseAllSounds pauses (and remembers) only the channels that are still playing. */
    @Redirect(method = "pauseAllSounds", at = @At(value = "INVOKE", target = "Ljava/util/Map;keySet()Ljava/util/Set;"))
    private Set<String> lads$playingChannels(Map<String, ISound> map) {
        lads$pausedChannels.clear();
        for (Map.Entry<String, ISound> entry : playingSounds.entrySet()) {
            if (isSoundPlaying(entry.getValue())) lads$pausedChannels.add(entry.getKey());
        }
        return new HashSet<String>(lads$pausedChannels);
    }

    /** resumeAllSounds resumes only what pauseAllSounds paused and is still listed; a second close resumes nothing. */
    @Redirect(method = "resumeAllSounds", at = @At(value = "INVOKE", target = "Ljava/util/Map;keySet()Ljava/util/Set;"))
    private Set<String> lads$pausedChannels(Map<String, ISound> map) {
        Set<String> resume = new HashSet<String>(lads$pausedChannels);
        resume.retainAll(playingSounds.keySet());
        lads$pausedChannels.clear();
        return resume;
    }

    @Inject(method = "stopSound", at = @At("HEAD"))
    private void lads$onStopSound(ISound sound, CallbackInfo ci) {
        String channel = invPlayingSounds.remove(sound);
        if (channel != null) {
            lads$pausedChannels.remove(channel);
            playingSounds.remove(channel);
        }

    }

    @Inject(method = "stopAllSounds", at = @At("HEAD"))
    private void lads$onStopAllSounds(CallbackInfo ci) {
        lads$pausedChannels.clear();
        invPlayingSounds.clear();
    }
}
