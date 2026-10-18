package com.thelads.core.v1_8_9.input;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.lwjgl.opengl.GL32;

/**
 * GL 3.2 fences on whichever GL32 the game has: LWJGL 2's (GLSync objects) or Pylon's LWJGL 3 (long handles; its LWJGL 2
 * compatibility layer has no GLSync overloads). A fence is an opaque Object here.
 */
final class GlFence189 {
    private static final MethodHandle FENCE, WAIT, DELETE;

    static {
        MethodHandle fence = null, waitSync = null, delete = null;
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        for (String type : new String[] { "org.lwjgl.opengl.GLSync", "long" }) {
            try {
                Class<?> sync = type.equals("long") ? long.class : Class.forName(type);
                fence = lookup.findStatic(GL32.class, "glFenceSync", MethodType.methodType(sync, int.class, int.class));
                waitSync = lookup.findStatic(GL32.class, "glClientWaitSync", MethodType.methodType(int.class, sync, int.class, long.class));
                delete = lookup.findStatic(GL32.class, "glDeleteSync", MethodType.methodType(void.class, sync));
                break;
            } catch (ReflectiveOperationException e) {
                fence = waitSync = delete = null;
            }
        }
        FENCE = fence == null ? null : fence.asType(MethodType.methodType(Object.class, int.class, int.class));
        WAIT = waitSync == null ? null : waitSync.asType(MethodType.methodType(int.class, Object.class, int.class, long.class));
        DELETE = delete == null ? null : delete.asType(MethodType.methodType(void.class, Object.class));
    }

    private GlFence189() {}

    static Object fence() throws Throwable {
        if (FENCE == null) throw new IllegalStateException("no GL32 fence API");
        return (Object) FENCE.invokeExact(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    /** Signalled (or unknown): never waits. */
    static boolean signalled(Object fence) throws Throwable {
        return fence == null || (int) WAIT.invokeExact(fence, 0, 0L) != GL32.GL_TIMEOUT_EXPIRED;
    }

    static void delete(Object fence) throws Throwable {
        if (fence != null) DELETE.invokeExact(fence);
    }
}
