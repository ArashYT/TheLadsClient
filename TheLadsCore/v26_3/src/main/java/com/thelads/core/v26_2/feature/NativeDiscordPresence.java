package com.thelads.core.v26_2.feature;

import com.thelads.core.client.discord.DiscordPresence;
import com.thelads.core.client.discord.DiscordRpcService;
import com.thelads.core.client.discord.NativeDiscordTransport;
import com.thelads.core.modules.DiscordRpcModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;

/** Samples game state only on Minecraft's thread and sends immutable data to the IPC worker. */
public final class NativeDiscordPresence {
    private static final DiscordRpcService SERVICE = new DiscordRpcService(NativeDiscordTransport::new);
    private static Object previousLevel;
    private static long started = System.currentTimeMillis() / 1000;
    private NativeDiscordPresence() {}
    public static void register() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SERVICE.close());
        Runtime.getRuntime().addShutdownHook(new Thread(SERVICE::close, "Lads Discord shutdown"));
    }
    public static void tick() {
        // Coming soon: do not connect or publish activity until application setup is complete.
    }
}
