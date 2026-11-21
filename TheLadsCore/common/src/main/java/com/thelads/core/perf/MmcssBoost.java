package com.thelads.core.perf;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

/**
 * Built-in Windows scheduling boost (the Lads remake of Windows Priority Boost): registers the calling thread, the render/main thread,
 * with the Multimedia Class Scheduler Service as a "Games" task (avrt.dll AvSetMmThreadCharacteristicsW), so Windows keeps giving it
 * CPU time while other programs run. The process priority class is not touched. Always on; stands down with
 * {@code -Dlads.mmcss=false} or when the real mod ({@code windows-priority-boost}) is installed. Only JNA calls both JNA 3.4 (1.8.9)
 * and 5.x have; any failure is logged once and swallowed.
 */
public final class MmcssBoost {
    private static boolean done;

    private MmcssBoost() {}

    /** Call once on the render thread during client start. Returns the log line text (also logged by the caller's logger). */
    public static synchronized String register() {
        if (done) return null;
        done = true;
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return "[Lads] MMCSS: skipped (not Windows)";
            if (!System.getProperty("os.arch", "").contains("64")) return "[Lads] MMCSS: skipped (32-bit Java)";
            String off = BuiltInRender.decide("mmcss", System.getProperty("lads.mmcss"), "windows-priority-boost");
            if (off != null) return "[Lads] MMCSS: off (" + off + ")";
            int before = threadPriority();
            Function register = Function.getFunction("avrt", "AvSetMmThreadCharacteristicsW");
            Memory taskIndex = new Memory(4);
            taskIndex.setInt(0, 0);
            Object handle = register.invoke(Pointer.class, new Object[] { new WString("Games"), taskIndex });
            if (handle == null) return "[Lads] MMCSS: not registered (AvSetMmThreadCharacteristicsW returned no handle)";
            return "[Lads] MMCSS: main thread registered as Games (index " + taskIndex.getInt(0) + ", thread priority " + before + " -> " + threadPriority() + ")";
        } catch (Throwable t) {
            return "[Lads] MMCSS: not registered (" + t + ")";
        }
    }

    /** Windows priority of the calling thread (GetThreadPriority), only for the log line. */
    private static int threadPriority() {
        Object thread = Function.getFunction("kernel32", "GetCurrentThread").invoke(Pointer.class, new Object[0]);
        return Function.getFunction("kernel32", "GetThreadPriority").invokeInt(new Object[] { thread });
    }
}
