package com.thelads.core.v26_2.feature.flashback.mixin;

import com.thelads.core.v26_2.feature.flashback.NativeFlashback;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every Flashback path to its replays (saving, the replay browser, combining, opening from disk) asks this one method. */
@Mixin(targets = "com.moulberry.flashback.Flashback")
abstract class ReplayFolderMixin {
    @Inject(method = "getReplayFolder", at = @At("HEAD"), cancellable = true)
    private static void lads$replayFolder(CallbackInfoReturnable<Path> cir) {
        Path folder = NativeFlashback.replayFolder();
        if (folder != null) cir.setReturnValue(folder);
    }
}
