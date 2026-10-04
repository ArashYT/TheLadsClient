package com.thelads.core.v1_8_9.mixin;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.Uninterruptibles;
import com.thelads.core.v1_8_9.feature.Cheats189;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.WorldType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world's own Allow Cheats as it loaded (Cheats189), before Essential puts its own switch on the world.
 *
 * Leaving a singleplayer world (loadWorld(null)): vanilla initiateShutdown queues "log every player out" for the server thread
 * and waits for it without a limit. A server that stops on its own at that moment (its owner's connection dropped, a kick) can
 * leave its tick loop without running the task, and the game froze (Not Responding). OptiFine skips the task once the server
 * has stopped running, which narrows that gap but doesn't close it. Wait in 50 ms slices and give up once the server has
 * stopped (its own shutdown already logged everyone out); while the server runs, nothing changes.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Inject(method = "loadAllWorlds", at = @At("TAIL"), require = 1)
    private void ladsCheatsLoaded(String saveName, String worldName, long seed, WorldType type, String generatorOptions, CallbackInfo ci) {
        Cheats189.loaded((IntegratedServer) (Object) this);
    }

    @Redirect(method = "initiateShutdown", require = 1,
        at = @At(value = "INVOKE", target = "Lcom/google/common/util/concurrent/Futures;getUnchecked(Ljava/util/concurrent/Future;)Ljava/lang/Object;"))
    private Object ladsAwaitLogout(Future<?> logout) {
        while (!logout.isDone() && !((MinecraftServer) (Object) this).isServerStopped()) {
            try {
                Uninterruptibles.getUninterruptibly(logout, 50, TimeUnit.MILLISECONDS);
            } catch (ExecutionException | TimeoutException ignored) {
                // failed: rethrown below as vanilla does; still queued: look at the server again
            }
        }
        return logout.isDone() ? Futures.getUnchecked(logout) : null;
    }
}
