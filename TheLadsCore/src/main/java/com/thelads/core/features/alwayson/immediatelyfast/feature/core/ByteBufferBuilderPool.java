package com.thelads.core.features.alwayson.immediatelyfast.feature.core;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast;

public class ByteBufferBuilderPool {
    private static final ReferenceList<Entry> FREE = new ReferenceArrayList<>();
    private static final ReferenceList<Entry> IN_USE = new ReferenceArrayList<>();
    private static final Reference2ObjectMap<ByteBufferBuilder, Entry> BUFFER_BUILDER_MAPPING = new Reference2ObjectOpenHashMap<>();

    private ByteBufferBuilderPool() {
    }

    public static ByteBufferBuilder borrowBufferBuilder() {
        Entry entry;
        RenderSystem.assertOnRenderThread();
        if (FREE.isEmpty()) {
            entry = new Entry(new ByteBufferBuilder(256));
        } else {
            entry = FREE.removeFirst();
            if (entry.bufferBuilder.pointer == 0L) {
                BUFFER_BUILDER_MAPPING.remove(entry.bufferBuilder);
                entry = new Entry(new ByteBufferBuilder(256));
            }
        }
        IN_USE.add(entry);
        BUFFER_BUILDER_MAPPING.put(entry.bufferBuilder, entry);
        entry.onBorrow();
        return entry.bufferBuilder;
    }

    public static void returnBufferBuilderSafe(ByteBufferBuilder bufferBuilder) {
        RenderSystem.assertOnRenderThread();
        Entry entry = BUFFER_BUILDER_MAPPING.get(bufferBuilder);
        if (entry == null || !IN_USE.remove(entry)) {
            return;
        }
        entry.onReturn();
        FREE.addFirst(entry);
    }

    public static int getSize() {
        return FREE.size() + IN_USE.size();
    }

    public static long getAllocatedBytes() {
        long total = 0L;
        for (Entry entry : FREE) {
            total += entry.bufferBuilder.capacity;
        }
        for (Entry entry : IN_USE) {
            total += entry.bufferBuilder.capacity;
        }
        return total;
    }

    public static void onEndFrame() {
        if (!IN_USE.isEmpty()) {
            IN_USE.removeIf(entry -> {
                if (entry.inUseOverMultipleFrames) {
                    ImmediatelyFast.LOGGER.warn("!!! Possible memory leak detected!!! A BufferBuilder was not returned to the pool. This is not a bug in ImmediatelyFast.");
                    ImmediatelyFast.LOGGER.warn("Allocation stack trace:");
                    if (entry.allocationStackTrace != null) {
                        for (StackTraceElement element : entry.allocationStackTrace) {
                            ImmediatelyFast.LOGGER.warn("\tat {}", element.toString());
                        }
                    } else {
                        ImmediatelyFast.LOGGER.warn("\t<No stack trace available. Enable debug_only_detailed_memory_leak_detection in the config to get stack traces>");
                    }
                    return true;
                }
                return false;
            });
            for (Entry entry2 : IN_USE) {
                entry2.inUseOverMultipleFrames = true;
            }
        }
        FREE.removeIf(entry -> {
            if (entry.shouldBeClosed()) {
                entry.bufferBuilder.close();
                BUFFER_BUILDER_MAPPING.remove(entry.bufferBuilder);
                return true;
            }
            return false;
        });
    }

    private static class Entry {
        private final ByteBufferBuilder bufferBuilder;
        private long lastAccessTime;
        private boolean inUseOverMultipleFrames;
        private StackTraceElement[] allocationStackTrace;

        public Entry(ByteBufferBuilder bufferBuilder) {
            this.bufferBuilder = bufferBuilder;
            this.lastAccessTime = System.currentTimeMillis();
        }

        public boolean shouldBeClosed() {
            return System.currentTimeMillis() - this.lastAccessTime > 60000L;
        }

        public void onBorrow() {
            this.lastAccessTime = System.currentTimeMillis();
            if (ImmediatelyFast.config.debug_only_detailed_memory_leak_detection) {
                this.allocationStackTrace = Thread.currentThread().getStackTrace();
            }
        }

        public void onReturn() {
            this.bufferBuilder.discard();
            this.inUseOverMultipleFrames = false;
            this.allocationStackTrace = null;
        }
    }
}
