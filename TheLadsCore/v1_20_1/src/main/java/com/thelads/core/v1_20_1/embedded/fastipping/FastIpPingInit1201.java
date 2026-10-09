// Modified by The Lads: new entry point for Lads Core (Fast IP Ping, LGPL-3.0); see META-INF/lads-sources/fastipping/NOTICE.txt.
package com.thelads.core.v1_20_1.embedded.fastipping;

import com.thelads.core.v1_20_1.perf.SmallModsProbe1201;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;

/** Entry point called by EmbeddedMods1201 while the original Fast IP Ping is not installed. */
public final class FastIpPingInit1201 {
    private static final AtomicInteger PATCHED = new AtomicInteger();

    private FastIpPingInit1201() {}

    public static void init() {
        SmallModsProbe1201.runOnce("fastipping", false, client -> {
            Thread thread = new Thread(FastIpPingInit1201::probe, "FastIpPing-probe");
            thread.setDaemon(true);
            thread.start();
        });
    }

    /** Called by the mixin each time an ip-only address was given its ip as host name. */
    public static void patched() {
        PATCHED.incrementAndGet();
    }

    /**
     * Resolves a loopback ip through the game's own resolver (the one the server list and the connect screen use). With the patch the
     * host name of the result is the ip itself, so no reverse lookup is needed; without it, getHostName() asks the system resolver
     * and answers "localhost". A name ("localhost") must keep working unchanged.
     */
    private static void probe() {
        try {
            long start = System.nanoTime();
            Optional<ResolvedServerAddress> byIp = ServerNameResolver.DEFAULT.resolveAddress(new ServerAddress("127.0.0.1", 25565));
            long ipMillis = (System.nanoTime() - start) / 1_000_000L;
            Optional<ResolvedServerAddress> byName = ServerNameResolver.DEFAULT.resolveAddress(new ServerAddress("localhost", 25565));
            boolean ipOk = byIp.isPresent() && "127.0.0.1".equals(byIp.get().getHostName()) && byIp.get().getPort() == 25565;
            boolean nameOk = byName.isPresent() && byName.get().asInetSocketAddress().getAddress().isLoopbackAddress();
            SmallModsProbe1201.result("fastipping", ipOk && nameOk && PATCHED.get() > 0,
                "ipHostName=" + byIp.map(ResolvedServerAddress::getHostName).orElse("-") + " (expected 127.0.0.1) resolvedMs=" + ipMillis
                    + " nameResolves=" + nameOk + " patchedCalls=" + PATCHED.get());
        } catch (Throwable failure) {
            SmallModsProbe1201.result("fastipping", false, "threw " + failure);
        }
    }
}
