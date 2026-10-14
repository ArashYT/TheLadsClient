package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.pack.PackAssetMask;
import java.util.List;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.LanguageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips re-parsing language metadata and reloading all {@code .lang} files during a resource-pack
 * switch when none of the added, removed, or reordered packs contain language files.
 */
@Mixin(LanguageManager.class)
public abstract class LanguageManagerReloadMixin189 {
    @Inject(method = "parseLanguageMetadata", at = @At("HEAD"), cancellable = true)
    private void ladsSkipUnchangedLangMetadata(List<IResourcePack> resourcesPacks, CallbackInfo ci) {
        if (PackAssetMask.shouldSkipLang()) {
            ci.cancel();
        }
    }

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"), cancellable = true)
    private void ladsSkipUnchangedLangReload(IResourceManager resourceManager, CallbackInfo ci) {
        if (PackAssetMask.shouldSkipLang()) {
            ci.cancel();
        }
    }
}
