package com.thelads.core.mixin.c2me;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.ishland.c2me.rewrites.chunksystem.common.structs.ChunkSystemExecutors", remap = false)
public class ReplayModC2MEHookMixin {

    private static boolean isReplayModActive() {
        try {
            Class<?> clazz = Class.forName("com.replaymod.recording.ReplayModRecording");
            Object instance = clazz.getField("instance").get(null);
            if (instance != null) {
                Object handler = clazz.getMethod("getConnectionEventHandler").invoke(instance);
                if (handler != null) {
                    Object listener = handler.getClass().getMethod("getPacketListener").invoke(handler);
                    if (listener != null) return true;
                }
            }
        } catch (Exception e) {}
        try {
            Class<?> clazz = Class.forName("com.replaymod.replay.ReplayModReplay");
            Object instance = clazz.getMethod("getInstance").invoke(null);
            if (instance != null) {
                Object handler = clazz.getMethod("getReplayHandler").invoke(instance);
                if (handler != null) return true;
            }
        } catch (Exception e) {}
        return false;
    }

    @Inject(method = "consolidatingRoot", at = @At("HEAD"), cancellable = true)
    private static void onConsolidatingRoot(Runnable initialCommand, CallbackInfo ci) {
        if (isReplayModActive()) {
            // Run synchronously to bypass concurrent chunk management during replays
            try {
                initialCommand.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
            ci.cancel();
        }
    }
}
