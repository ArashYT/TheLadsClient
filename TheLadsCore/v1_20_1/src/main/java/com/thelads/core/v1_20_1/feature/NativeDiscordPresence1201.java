package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.discord.DiscordPresence.Place;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.DiscordRpcModule;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

public final class NativeDiscordPresence1201 {
    private static final String VERSION = SharedConstants.getCurrentVersion().getName();

    private NativeDiscordPresence1201() {}

    public static void tick() {
        if (!(ModuleManager.getInstance().getModule("DiscordRPC") instanceof DiscordRpcModule module)) return;
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var remote = mc.getCurrentServer();
        Place place = mc.level == null ? Place.MENU : server != null ? Place.SINGLEPLAYER : Place.MULTIPLAYER;
        module.publish(
            place,
            VERSION,
            remote == null ? null : remote.ip,
            server == null ? null : server.getWorldData().getLevelName(),
            mc.level == null ? null : mc.level.dimension().location().toString(),
            mc.screen
        );
    }
}
