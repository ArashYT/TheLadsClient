package com.thelads.core.mixin;

import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.PackResources;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.client.benchmark.BenchmarkTracker;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.List;

@Mixin(ReloadableResourceManager.class)
public class ReloadableResourceManagerMixin {

    @Inject(method = "createReload", at = @At("HEAD"))
    private void onCreateReloadHead(Executor backgroundExecutor, Executor gameExecutor, CompletableFuture<Unit> initialStage, List<PackResources> packs, CallbackInfoReturnable<ReloadInstance> cir) {
        BenchmarkTracker.setResourceLoadStartTime(System.nanoTime());
    }

    @Inject(method = "createReload", at = @At("RETURN"))
    private void onCreateReloadReturn(Executor backgroundExecutor, Executor gameExecutor, CompletableFuture<Unit> initialStage, List<PackResources> packs, CallbackInfoReturnable<ReloadInstance> cir) {
        ReloadInstance reload = cir.getReturnValue();
        if (reload != null) {
            reload.done().thenRun(() -> {
                BenchmarkTracker.setResourceLoadEndTime(System.nanoTime());
            });
        }
    }
}
