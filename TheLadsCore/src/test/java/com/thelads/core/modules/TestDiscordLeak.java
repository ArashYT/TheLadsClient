package com.thelads.core.modules;

import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordEventHandlers;
import org.junit.jupiter.api.Test;

public class TestDiscordLeak {
    @Test
    public void testLeak() throws InterruptedException {
        DiscordRPC lib = DiscordRPC.INSTANCE;
        int startThreads = Thread.activeCount();
        System.out.println("Start threads: " + startThreads);
        
        for (int i = 0; i < 50; i++) {
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            handlers.ready = (user) -> {};
            lib.Discord_Initialize("123456789012345678", handlers, true, null);
            Thread.sleep(50);
            lib.Discord_Shutdown();
            Thread.sleep(50);
        }
        
        int endThreads = Thread.activeCount();
        System.out.println("End threads: " + endThreads);
    }
}
