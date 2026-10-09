// Derived from Server Pinger Fixer 1.0.6 / 1.1.1 by JustAlittleWolf (MIT); see META-INF/lads-sources/serverpingerfixer/LICENSE.
// Modified by The Lads: ported to Minecraft 1.20.1 (Fabric, Mojang names) from the Lads 26.3 embedded copy; adds a QA probe.
package com.thelads.core.v1_20_1.embedded.serverpingerfixer;

import com.thelads.core.v1_20_1.perf.SmallModsProbe1201;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;

/** Entry point called by EmbeddedMods1201 while the original Server Pinger Fixer is not installed. */
public final class ServerPingerFixerInit1201 {
    private static volatile ThreadPoolExecutor installed;

    private ServerPingerFixerInit1201() {}

    public static void init() {
        SmallModsProbe1201.runOnce("serverpingerfixer", false, client -> {
            Thread thread = new Thread(ServerPingerFixerInit1201::probe, "ServerPingerFixer-probe");
            thread.setDaemon(true);
            thread.start();
        });
    }

    /** Called from the mixin once it has replaced the list's ping pool. */
    public static void poolInstalled(ThreadPoolExecutor pool) {
        installed = pool;
    }

    /** Twelve pings that all wait for each other: vanilla's five-thread pool can never run them at once. */
    private static void probe() {
        try {
            // The pool lives in ServerSelectionList's static initializer, which a title-screen session has not run yet.
            Class.forName(ServerSelectionList.class.getName(), true, ServerSelectionList.class.getClassLoader());
            ThreadPoolExecutor pool = installed;
            if (pool == null) {
                SmallModsProbe1201.result("serverpingerfixer", false, "the mixin did not replace ServerSelectionList.THREAD_POOL");
                return;
            }
            int tasks = 12;
            CountDownLatch together = new CountDownLatch(tasks);
            for (int i = 0; i < tasks; i++) {
                pool.submit(() -> {
                    together.countDown();
                    try {
                        together.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            boolean ok = together.await(10, TimeUnit.SECONDS);
            SmallModsProbe1201.result("serverpingerfixer", ok && pool.getMaximumPoolSize() == Integer.MAX_VALUE,
                "maxPoolSize=" + pool.getMaximumPoolSize() + " startedTogether=" + (tasks - together.getCount()) + "/" + tasks);
        } catch (Throwable failure) {
            SmallModsProbe1201.result("serverpingerfixer", false, "threw " + failure);
        }
    }
}
