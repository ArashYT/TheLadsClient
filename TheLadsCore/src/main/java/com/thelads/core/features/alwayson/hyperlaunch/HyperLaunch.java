package com.thelads.core.features.alwayson.hyperlaunch;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Constructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HyperLaunch {
    public static final String MOD_ID = "hyperlaunch";
    public static final Logger LOGGER = LoggerFactory.getLogger("hyperlaunch");
    private static final AtomicInteger WORKER_COUNTER = new AtomicInteger();
    private static final AtomicInteger IO_WORKER_COUNTER = new AtomicInteger();
    private static final int BOOTSTRAP_PARALLELISM = Math.max(4, Integer.getInteger("hyperlaunch.parallelism", Runtime.getRuntime().availableProcessors()));
    private static final int IO_PARALLELISM = Math.max(BOOTSTRAP_PARALLELISM, Integer.getInteger("hyperlaunch.ioParallelism", Runtime.getRuntime().availableProcessors() * 2));
    
    private static final ExecutorService BOOTSTRAP_EXECUTOR = new ForkJoinPool(BOOTSTRAP_PARALLELISM, pool -> {
        ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        thread.setName("HyperLaunch-Bootstrap-" + WORKER_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }, (thread, throwable) -> LOGGER.error("HyperLaunch bootstrap worker failed", throwable), true);
    
    private static final ExecutorService IO_EXECUTOR = new ForkJoinPool(IO_PARALLELISM, pool -> {
        ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        thread.setName("HyperLaunch-IO-" + IO_WORKER_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }, (thread, throwable) -> LOGGER.error("HyperLaunch IO worker failed", throwable), true);
    
    public static ExecutorService bootstrapExecutor() {
        return BOOTSTRAP_EXECUTOR;
    }

    public static ExecutorService ioExecutor() {
        return IO_EXECUTOR;
    }

    public static ExecutorService minecraftBootstrapExecutor() {
        return BOOTSTRAP_EXECUTOR;
    }

    public static ExecutorService minecraftIoExecutor() {
        return IO_EXECUTOR;
    }

    public static int maxBackgroundThreads() {
        return IO_PARALLELISM;
    }

    public static void init() {
        LOGGER.info("HyperLaunch active with bootstrap parallelism {} and IO parallelism {}", BOOTSTRAP_PARALLELISM, IO_PARALLELISM);
    }
}
