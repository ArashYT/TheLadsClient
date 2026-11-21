package com.thelads.core.v26_2.feature.packs.mixin;

import com.thelads.core.perf.ParallelPacks;
import com.thelads.core.v26_2.feature.packs.ParallelPacks26;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Downloads the packs of one batch up to four at a time; see ParallelPacks26. Off with -Dlads.parallelpacks=false. */
@Mixin(DownloadQueue.class)
public abstract class DownloadQueueParallelMixin {
    @Shadow @Final private Path cacheDir;

    @Shadow
    private DownloadQueue.BatchResult runDownload(DownloadQueue.BatchConfig config, Map<UUID, DownloadQueue.DownloadRequest> requests) {
        throw new AssertionError();
    }

    @Inject(method = "runDownload", at = @At("HEAD"), cancellable = true)
    private void lads$parallel(DownloadQueue.BatchConfig config, Map<UUID, DownloadQueue.DownloadRequest> requests, CallbackInfoReturnable<DownloadQueue.BatchResult> cir) {
        if (requests.size() < 2 || !ParallelPacks.enabled()) return;
        cir.setReturnValue(ParallelPacks26.run(config, requests, this.cacheDir, (c, r) -> this.runDownload(c, r)));
    }
}
