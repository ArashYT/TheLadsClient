package com.thelads.ladsapi.paper;

import com.thelads.ladsapi.LadsCore;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PaperPlayerHandleTest {

    interface CraftPlayerMock extends Player {
        Set<String> channels();
    }

    @Test
    public void testChannelsReflectionOnSend() {
        Set<String> liveChannels = new HashSet<>();
        AtomicBoolean sendCalled = new AtomicBoolean(false);

        CraftPlayerMock mockPlayer = (CraftPlayerMock) Proxy.newProxyInstance(
                CraftPlayerMock.class.getClassLoader(),
                new Class<?>[]{CraftPlayerMock.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getName().equals("channels")) {
                            return liveChannels;
                        }
                        if (method.getName().equals("sendPluginMessage")) {
                            sendCalled.set(true);
                            return null;
                        }
                        if (method.getName().equals("getUniqueId")) {
                            return UUID.randomUUID();
                        }
                        if (method.getName().equals("getName")) {
                            return "TestPaperPlayer";
                        }
                        return null;
                    }
                }
        );

        PaperPlayerHandle handle = new PaperPlayerHandle(null, mockPlayer);
        byte[] payload = "{\"t\":\"hello\"}".getBytes();
        handle.send(payload);

        assertTrue(liveChannels.contains(LadsCore.CHANNEL), "send() must reflectively add lads:api to CraftPlayer.channels()");
        assertTrue(sendCalled.get(), "sendPluginMessage should have been called");
    }
}
