// Derived from Server Pinger Fixer 1.0.6 / 1.1.1 by JustAlittleWolf (MIT); see META-INF/lads-sources/serverpingerfixer/LICENSE.
package com.thelads.core.v1_21_11.embedded.serverpingerfixer.mixin;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pings every server in the list at once: an unbounded pool replaces vanilla's fixed five-thread one. */
@Mixin(ServerSelectionList.class)
public class ServerSelectionListMixin {
    @Mutable
    @Final
    @Shadow
    static ThreadPoolExecutor THREAD_POOL;

    @Unique
    private static final long TIMEOUT_SECONDS = 30L;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void overrideThreadPool(CallbackInfo ci) {
        ThreadFactory vanillaThreadFactory = THREAD_POOL.getThreadFactory();
        THREAD_POOL.shutdown();
        THREAD_POOL = new ThreadPoolExecutor(
            1,
            Integer.MAX_VALUE,
            TIMEOUT_SECONDS,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            vanillaThreadFactory
        );
    }
}
