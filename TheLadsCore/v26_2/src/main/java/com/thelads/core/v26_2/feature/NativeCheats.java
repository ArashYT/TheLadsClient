package com.thelads.core.v26_2.feature;

import com.thelads.core.client.WorldCheats;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PrimaryLevelData;

/**
 * Cheats stay as the host set them (WorldCheats). When a world opens, joined players get the host's last choice and
 * Essential's switch takes the world's own; after that, Essential's Cheats switch changing is the host's choice, which
 * the world takes and sends to every player.
 */
public final class NativeCheats {
    private static IntegratedServer opened;
    private NativeCheats() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(NativeCheats::tick);
    }

    static void tick(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) { opened = null; return; }
        if (server != opened) {
            opened = server;
            Boolean guests = WorldCheats.guests(folder(server));
            if (guests != null) server.setCommandsAllowedForOtherPlayers(guests);
        }
    }

    public static Path folder(IntegratedServer server) { return server.getWorldPath(LevelResource.ROOT); }

    /** The world's own switch (level.dat's), which Essential's answer for LevelSettings.allowCommands() hides. */
    static boolean own(IntegratedServer server) {
        return server.getWorldData() instanceof PrimaryLevelData data ? data.settings.allowCommands : server.getWorldData().isAllowCommands();
    }
}
