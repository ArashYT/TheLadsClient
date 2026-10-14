package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.pack.PackAssetMask;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips tearing down and re-initializing the OpenAL {@code SoundManager} during a resource-pack
 * switch when none of the added, removed, or reordered packs contain sound assets.
 */
@Mixin(SoundHandler.class)
public abstract class SoundHandlerReloadMixin189 {
    @Inject(method = "onResourceManagerReload", at = @At("HEAD"), cancellable = true)
    private void ladsSkipUnchangedSoundReload(IResourceManager resourceManager, CallbackInfo ci) {
        if (PackAssetMask.shouldSkipSounds()) {
            ci.cancel();
        }
    }
}
