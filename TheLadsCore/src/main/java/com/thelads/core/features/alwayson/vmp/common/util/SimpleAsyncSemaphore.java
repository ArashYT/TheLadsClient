package com.thelads.core.features.alwayson.vmp.common.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;

public class SimpleAsyncSemaphore {
    private final Queue<CompletableFuture<Void>> queue = new ConcurrentLinkedQueue<>();
    private int permits;

    public SimpleAsyncSemaphore(long permits) {
        this.permits = (int) permits;
    }

    public CompletableFuture<Void> acquire() {
        CompletableFuture<Void> future;
        synchronized (this) {
            if (permits > 0) {
                permits--;
                return CompletableFuture.completedFuture(null);
            }
            future = new CompletableFuture<>();
            queue.add(future);
        }
        future.whenComplete((v, ex) -> {
            if (future.isCancelled() || future.isCompletedExceptionally()) {
                synchronized (this) {
                    queue.remove(future);
                }
            }
        });
        return future;
    }

    public void release() {
        while (true) {
            CompletableFuture<Void> next;
            synchronized (this) {
                next = queue.poll();
                if (next == null) {
                    permits++;
                    return;
                }
            }
            if (next.complete(null)) {
                return;
            }
        }
    }
}
