package com.thelads.core.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.moulberry.flashback.record.Recorder")
public class RecorderMixin {
    @Redirect(
        method = "writeChunkDataSnapshot",
        at = @At(value = "INVOKE", target = "Ljava/lang/Runtime;availableProcessors()I"),
        require = 1,
        remap = false
    )
    private int forceSyncChunkSnapshot(Runtime runtime) {
        // Force Flashback to use synchronous chunk snapshotting.
        return 1; 
    }
}
