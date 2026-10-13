package com.thelads.core.v1_20_1.game.cheats;

import com.thelads.core.client.WorldCheats;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Cheats stay as the host set them (WorldCheats). When a world opens, joined players get the host's last choice and Essential's
 * switch takes the world's own; after that, Essential's Cheats switch changing is the host's choice, which the world takes and sends
 * to every player. Without Essential nothing but the joined players' choice is kept.
 */
public final class Cheats1201 {
    private static IntegratedServer opened;

    private Cheats1201() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Cheats1201::tick);
    }

    static void tick(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) { opened = null; return; }
        if (server != opened) {
            opened = server;
            Boolean guests = WorldCheats.guests(folder(server));
            if (guests != null) server.execute(() -> server.getPlayerList().setAllowCheatsForAllPlayers(guests));
        }
    }

    public static Path folder(IntegratedServer server) { return server.getWorldPath(LevelResource.ROOT); }
}
