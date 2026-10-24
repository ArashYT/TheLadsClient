// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v1_20_1.perf.dynamicfps.mixin;

import com.mojang.blaze3d.audio.Listener;
import com.thelads.core.v1_20_1.perf.dynamicfps.feature.volume.SmoothVolumeHandler;
import com.thelads.core.v1_20_1.perf.dynamicfps.util.Logging;
import com.thelads.core.v1_20_1.perf.dynamicfps.util.duck.DuckSoundEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class SoundEngineMixin implements DuckSoundEngine {
	@Shadow
	private boolean loaded;

	@Shadow
	@Final
	private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

	@Shadow
	@Final
	private Listener listener;

	@Shadow
	@Final
	private Options options;

	@Shadow
	private float calculateVolume(SoundInstance instance) {
		throw new RuntimeException("Failed to find SoundEngine.calculateVolume.");
	}

	@Override
	public void dynamic_fps$updateVolume(SoundSource source) {
		if (!this.loaded) {
			return;
		}

		// The master volume is the listener's gain in 1.20.1, not part of each sound's volume.
		if (source == SoundSource.MASTER) {
			this.listener.setGain(this.options.getSoundSourceVolume(SoundSource.MASTER));
			return;
		}

		// Create a copy of all currently active sounds, as iterating over this collection
		// Can throw if a sound instance stops playing while we are updating sound volumes
		List<SoundInstance> sounds;

		try {
			sounds = new ArrayList<>(this.instanceToChannel.keySet());
		} catch (Throwable e) {
			Logging.getLogger().error("Unable to update source volume!", e);
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		// Using our copy should now be safe as long as we check the channel handle exists
		for (SoundInstance instance : sounds) {
			ChannelAccess.ChannelHandle handle = this.instanceToChannel.get(instance);

			if (handle == null || !instance.getSource().equals(source)) {
				continue;
			}

			float volume = this.calculateVolume(instance);

			// When setting the volume to zero we pause music but cancel other types of sounds
			// This results in a less jarring experience when quickly tabbing out and back in.
			// Also fixes this compat bug: https://github.com/juliand665/Dynamic-FPS/issues/55
			boolean isMusic = instance.getSource().equals(SoundSource.MUSIC) || instance.getSource().equals(SoundSource.RECORDS);
			boolean playsPaused = isMusic || instance.getSource().equals(SoundSource.AMBIENT);

			handle.execute(channel -> {
				if (volume <= 0.0f) {
					// Pause music unconditionally when volume is zero
					// Otherwise if vanilla doesn't pause the sound set the volume to zero
					// This allows long sounds (e.g. sonic boom) to be heard when tabbing back in
					if (isMusic) {
						channel.pause();
					} else if (!minecraft.isPaused()) {
						channel.setVolume(volume);
					}
				} else {
					if (playsPaused && !minecraft.isPaused()) {
						channel.unpause();
					}

					channel.setVolume(volume);
				}
			});
		}
	}

	/**
	 * Cancels playing sounds while we are overwriting the volume to be off.
	 * <br>
	 * This is done in favor of actually setting the volume to zero because it
	 * Allows pausing and resuming the sound engine without cancelling all active sounds.
	 */
	@Inject(method = "play", at = @At("HEAD"), cancellable = true, require = 1)
	private void lads$play(SoundInstance instance, CallbackInfo callbackInfo) {
		if (instance != null && instance.getLocation() != null && com.thelads.core.client.hypixel.SilentLobby.shouldMuteSound(instance.getLocation().getPath())) {
			callbackInfo.cancel();
			return;
		}
		if (SmoothVolumeHandler.volumeMultiplier(instance.getSource()) == 0.0f) {
			callbackInfo.cancel();
		}
	}

	/**
	 * Cancels scheduling sounds while we are overwriting the volume to be off.
	 */
	@Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true, require = 1)
	private void lads$playDelayed(SoundInstance instance, int delay, CallbackInfo callbackInfo) {
		if (SmoothVolumeHandler.volumeMultiplier(instance.getSource()) == 0.0f) {
			callbackInfo.cancel();
		}
	}
}
