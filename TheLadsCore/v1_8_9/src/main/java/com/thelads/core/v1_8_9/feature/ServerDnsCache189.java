package com.thelads.core.v1_8_9.feature;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Caches and asynchronously pre-resolves server hostnames to InetAddress.
 * When servers are pinged in the multiplayer server list or entered, the resolved
 * IP address is cached so GuiConnecting connects immediately without redundant DNS delay.
 */
public final class ServerDnsCache189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    private static final Map<String, InetAddress> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Lads-DNS-PreResolver");
        t.setDaemon(true);
        return t;
    });

    private ServerDnsCache189() {}

    /** Resolves the host, returning the cached InetAddress if available, or resolving and caching. */
    public static InetAddress resolve(String host) throws UnknownHostException {
        if (host == null || host.isEmpty()) return InetAddress.getByName(host);
        InetAddress cached = CACHE.get(host);
        if (cached != null) return cached;
        InetAddress resolved = InetAddress.getByName(host);
        CACHE.put(host, resolved);
        return resolved;
    }

    /** Pre-resolves a host asynchronously in the background if not already in the cache. */
    public static void preResolve(String host) {
        if (host == null || host.isEmpty() || CACHE.containsKey(host)) return;
        EXECUTOR.execute(() -> {
            try {
                InetAddress resolved = InetAddress.getByName(host);
                CACHE.put(host, resolved);
                LOGGER.debug("Server DNS pre-resolved: {} -> {}", host, resolved);
            } catch (UnknownHostException ignored) {
                // Handled normally when GuiConnecting attempts connection
            }
        });
    }

    /** Store an already resolved address. */
    public static void put(String host, InetAddress address) {
        if (host != null && address != null) {
            CACHE.put(host, address);
        }
    }
}
