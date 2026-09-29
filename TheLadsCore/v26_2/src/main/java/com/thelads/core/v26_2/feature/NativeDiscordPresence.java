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
        if (!(NativeQualityOfLife.module("DiscordRPC") instanceof DiscordRpcModule module)) return;
        boolean enabled = module.isEnabled() && module.shareActivity.get();
        String id = module.applicationId.getValue().trim();
        if (!enabled || !DiscordRpcService.validApplicationId(id)) {
            SERVICE.submit(new DiscordRpcService.Desired(enabled, id, null, null));
            module.setStatus(SERVICE.status()); return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (previousLevel != minecraft.level) { previousLevel = minecraft.level; started = System.currentTimeMillis() / 1000; }
        DiscordPresence.Place place = minecraft.level == null || minecraft.player == null ? DiscordPresence.Place.MENU
            : minecraft.hasSingleplayerServer() ? DiscordPresence.Place.SINGLEPLAYER : DiscordPresence.Place.MULTIPLAYER;
        var privacy = new DiscordPresence.Privacy(module.shareServerAddress.get(), module.shareWorldName.get(),
            module.showDimension.get(), module.showElapsed.get(), module.detailLevel.getIndex());
        // Do not even read private display data unless its individual publication option is selected.
        String server = privacy.serverAddress() && minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().ip : "";
        String world = privacy.worldName() && minecraft.getSingleplayerServer() != null
            ? minecraft.getSingleplayerServer().getWorldData().getLevelName() : "";
        String dimension = privacy.dimension() && minecraft.level != null ? minecraft.level.dimension().identifier().toString() : "";
        var game = new DiscordPresence.Game(place, "26.2", server, world, dimension, started);
        SERVICE.submit(new DiscordRpcService.Desired(enabled, id, privacy, DiscordPresence.from(game, privacy)));
        module.setStatus(SERVICE.status());
    }
}
