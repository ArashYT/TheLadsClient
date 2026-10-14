package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.pack.PackAssetMask;
import java.util.Map;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips redundant {@code sounds.json} parsing and OpenAL {@code SoundEngine.reload()} teardown
 * in 26.3 during a resource-pack switch when no sound assets were added, removed, or reordered.
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerReloadMixin26 {
    @Shadow @Final private Map<Identifier, WeighedSoundEvents> registry;
    @Shadow @Final private Map<Identifier, Resource> soundCache;

    @Inject(
        method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/client/sounds/SoundManager$Preparations;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ladsSkipUnchangedSoundPrepare(
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfoReturnable<Object> cir) {
        if (!this.registry.isEmpty() && PackAssetMask.shouldSkipSounds()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(
        method = "apply(Lnet/minecraft/client/sounds/SoundManager$Preparations;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ladsSkipUnchangedSoundApply(
            @Coerce Object preparations,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo ci) {
        if (preparations == null || (!this.registry.isEmpty() && PackAssetMask.shouldSkipSounds())) {
            if (PackAssetMask.anySelectedPackHasSounds() && resourceManager != null) {
                this.soundCache.clear();
                this.soundCache.putAll(resourceManager.listResources("sounds", p -> p.getPath().endsWith(".ogg")));
            }
            ci.cancel();
        }
    }
}
