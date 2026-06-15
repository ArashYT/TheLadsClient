package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import club.minnced.discord.rpc.DiscordRPC;

public class DiscordRpcStressTest {
    @Test
    public void testToggleLeak() throws InterruptedException {
        DiscordRpcModule module = new DiscordRpcModule();
        
        int initialThreads = Thread.activeCount();
        
        for (int i = 0; i < 50; i++) {
            module.onEnable();
            Thread.sleep(50);
            module.onDisable();
            Thread.sleep(50);
        }
        
        int finalThreads = Thread.activeCount();
        System.out.println("Initial threads: " + initialThreads);
        System.out.println("Final threads: " + finalThreads);
        
        // If finalThreads is significantly larger, we have a leak!
    }
}
