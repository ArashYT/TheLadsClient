package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.pack.PackAssetMask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips recompiling all render pipelines and post-processing chains in {@code ShaderManager.reload}
 * during a 26.3 resource-pack switch when none of the added, removed, or reordered packs contain shaders.
 */
@Mixin(ShaderManager.class)
public abstract class ShaderManagerReloadMixin26 {
    @Unique private boolean lads$initialized = false;

    @Inject(method = "apply", at = @At("RETURN"))
    private void ladsMarkInitialized(CallbackInfo ci) {
        this.lads$initialized = true;
    }

    @Inject(method = "reload", at = @At("HEAD"), cancellable = true)
    private void ladsSkipUnchangedShaderReload(
            PreparableReloadListener.SharedState sharedState,
            Executor prepareExecutor,
            PreparableReloadListener.PreparationBarrier barrier,
            Executor applyExecutor,
            CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        if (this.lads$initialized && PackAssetMask.shouldSkipShaders()) {
            cir.setReturnValue(barrier.wait(null));
        }
    }
}
