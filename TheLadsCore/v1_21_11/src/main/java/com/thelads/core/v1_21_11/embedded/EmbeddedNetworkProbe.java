package com.thelads.core.v1_21_11.embedded;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.util.concurrent.ThreadPoolExecutor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** QA only (-Dthelads.verifyAutoWorld): shows the embedded server-list mixins applied, without opening any screen. */
final class EmbeddedNetworkProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore/EmbeddedProbe");

    private EmbeddedNetworkProbe() {}

    static void run() {
        if (EmbeddedMods.active("serverpingerfixer")) {
            try {
                for (Field field : ServerSelectionList.class.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && field.getType() == ThreadPoolExecutor.class) {
                        field.setAccessible(true);
                        LOGGER.info("Server Pinger Fixer: server list ping pool max {}", ((ThreadPoolExecutor) field.get(null)).getMaximumPoolSize());
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("Server Pinger Fixer probe failed", e);
            }
        }
        if (EmbeddedMods.active("fastipping")) {
            // A literal IP keeps its text as host name once patched ("127.0.0.1/127.0.0.1"); vanilla leaves it unset ("/127.0.0.1").
            InetAddress address = ServerAddressResolver.SYSTEM.resolve(ServerAddress.parseString("127.0.0.1"))
                .map(ResolvedServerAddress::asInetSocketAddress).map(socket -> socket.getAddress()).orElse(null);
            LOGGER.info("Fast IP Ping: 127.0.0.1 resolves to {}", address);
        }
    }
}
