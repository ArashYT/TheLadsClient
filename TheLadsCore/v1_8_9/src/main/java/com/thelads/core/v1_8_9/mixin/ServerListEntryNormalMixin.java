package com.thelads.core.v1_8_9.mixin;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import net.minecraft.client.gui.ServerListEntryNormal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerListEntryNormal.class)
public abstract class ServerListEntryNormalMixin {
    @Unique
    private static final ExecutorService ladsPingerPool = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat("Lads Server Pinger #%d").setDaemon(true).build()
    );

    @Redirect(method = "drawEntry", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ThreadPoolExecutor;submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;"))
    private Future<?> ladsParallelPing(ThreadPoolExecutor executor, Runnable task) {
        return ladsPingerPool.submit(task);
    }
}
