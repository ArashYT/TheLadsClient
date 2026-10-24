package com.thelads.ladsapi.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VelocityPlayerHandleTest {

    @Test
    public void testVelocityWorldReturnsBackendServerName() {
        ServerInfo serverInfo = new ServerInfo("lobby", new InetSocketAddress("127.0.0.1", 25565));

        RegisteredServer registeredServer = (RegisteredServer) Proxy.newProxyInstance(
                RegisteredServer.class.getClassLoader(),
                new Class<?>[]{RegisteredServer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getServerInfo")) {
                        return serverInfo;
                    }
                    return null;
                }
        );

        ServerConnection serverConnection = (ServerConnection) Proxy.newProxyInstance(
                ServerConnection.class.getClassLoader(),
                new Class<?>[]{ServerConnection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getServer")) {
                        return registeredServer;
                    }
                    return null;
                }
        );

        Player mockPlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getCurrentServer")) {
                        return Optional.of(serverConnection);
                    }
                    if (method.getName().equals("getUniqueId")) {
                        return UUID.fromString("00000000-0000-0000-0000-000000000001");
                    }
                    if (method.getName().equals("getUsername")) {
                        return "VelocityPlayer1";
                    }
                    return null;
                }
        );

        VelocityPlayerHandle handle = new VelocityPlayerHandle(mockPlayer);
        assertEquals("lobby", handle.world(), "On proxy, world() must return the backend server name");
        assertEquals("VelocityPlayer1", handle.name());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), handle.uuid());
    }

    @Test
    public void testVelocityPlatformIsProxy() {
        VelocityPlatform platform = new VelocityPlatform(null, null, null);
        assertTrue(platform.isProxy(), "Velocity platform must report isProxy() == true");
        assertEquals("velocity", platform.id());
    }
}
