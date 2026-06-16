/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.logging.LogUtils
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.features.alwayson.clientsort.interaction;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

public class InteractionManager {
    public static final Waiter TICK_WAITER = type -> type == TriggerType.TICK;
    private static final ArrayDeque<InteractionEvent> eventQueue = new ArrayDeque();
    private static final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private static ScheduledFuture<?> tickFuture;
    private static Waiter waiter;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void push(InteractionEvent event) {
        ArrayDeque<InteractionEvent> arrayDeque = eventQueue;
        synchronized (arrayDeque) {
            eventQueue.add(event);
            if (waiter == null) {
                InteractionManager.triggerSend(TriggerType.INITIAL);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void pushAll(Collection<InteractionEvent> events) {
        ArrayDeque<InteractionEvent> arrayDeque = eventQueue;
        synchronized (arrayDeque) {
            eventQueue.addAll(events);
            if (waiter == null) {
                InteractionManager.triggerSend(TriggerType.INITIAL);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void clear() {
        ArrayDeque<InteractionEvent> arrayDeque = eventQueue;
        synchronized (arrayDeque) {
            eventQueue.clear();
            waiter = null;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void triggerSend(TriggerType type) {
        ArrayDeque<InteractionEvent> arrayDeque = eventQueue;
        synchronized (arrayDeque) {
            if (waiter == null || waiter.trigger(type)) {
                do {
                    InteractionEvent event;
                    if ((event = eventQueue.poll()) == null) {
                        waiter = null;
                        break;
                    }
                    InteractionManager.doSendEvent(event);
                } while (waiter.trigger(TriggerType.INITIAL));
            }
        }
    }

    private static void doSendEvent(InteractionEvent event) {
        Waiter blockingWaiter;
        waiter = blockingWaiter = tt -> false;
        Minecraft.getInstance().execute(() -> {
            ArrayDeque<InteractionEvent> arrayDeque = eventQueue;
            synchronized (arrayDeque) {
                if (waiter == blockingWaiter) {
                    waiter = event.send();
                }
            }
        });
    }

    public static void setTickRate(long intervalMs) {
        if (tickFuture != null) {
            tickFuture.cancel(false);
        }
        tickFuture = executor.scheduleAtFixedRate(InteractionManager::tick, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private static void tick() {
        try {
            InteractionManager.triggerSend(TriggerType.TICK);
        }
        catch (Exception e) {
            LogUtils.getLogger().error("Error while ticking InteractionManager", (Throwable)e);
        }
    }

    static {
        waiter = null;
    }

    @FunctionalInterface
    public static interface Waiter {
        public boolean trigger(TriggerType var1);

        public static Waiter equal(TriggerType type) {
            return type::equals;
        }
    }

    public static enum TriggerType {
        INITIAL,
        GUI_CONFIRM,
        TICK;

    }

    @FunctionalInterface
    public static interface InteractionEvent {
        public Waiter send();
    }

    public static class CallbackEvent
    implements InteractionEvent {
        private final Supplier<Waiter> callback;

        public CallbackEvent(Supplier<Waiter> callback) {
            this.callback = callback;
        }

        @Override
        public Waiter send() {
            return this.callback.get();
        }
    }
}
