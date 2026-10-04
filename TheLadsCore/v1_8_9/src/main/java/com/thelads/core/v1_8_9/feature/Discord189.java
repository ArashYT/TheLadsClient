package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.discord.DiscordPresence.Place;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.DiscordRpcModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.integrated.IntegratedServer;

/** Samples game state on Minecraft's thread for Discord presence, as 26.x NativeDiscordPresence; DiscordRpcModule sends it. */
public final class Discord189 {
    private Discord189() {}
    public static void tick(Minecraft mc) {
        Module module = ModuleManager.getInstance().getModule("DiscordRPC");
        if (!(module instanceof DiscordRpcModule)) return;
        IntegratedServer server = mc.getIntegratedServer();
        ServerData remote = mc.getCurrentServerData();
        Place place = mc.theWorld == null ? Place.MENU : server != null ? Place.SINGLEPLAYER : Place.MULTIPLAYER;
        String dimension = null;
        if (mc.theWorld != null) {
            int id = mc.theWorld.provider.getDimensionId();
            dimension = id == -1 ? "minecraft:the_nether" : id == 1 ? "minecraft:the_end" : "minecraft:overworld";
        }
        ((DiscordRpcModule) module).publish(place, "1.8.9", remote == null ? null : remote.serverIP,
            server == null ? null : server.getWorldName(), dimension, mc.currentScreen);
    }
}
