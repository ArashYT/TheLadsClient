package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.WorldCheats;
import java.io.File;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.storage.WorldInfo;

/**
 * Cheats stay as the host set them (WorldCheats). The world's own Allow Cheats, as it loaded, wins over Essential's
 * switch when the world opens (Essential then writes it back itself). Open to LAN with Allow Cheats keeps the host's
 * cheats in the world, and the LAN screen starts on the host's last choice for joined players.
 */
public final class Cheats189 {
    private static IntegratedServer opened;
    private static volatile boolean own;
    private static boolean agreed;
    private Cheats189() {}

    /** IntegratedServer.loadAllWorlds: the world's own switch, read before Essential puts its own on the world. */
    public static void loaded(IntegratedServer server) {
        own = server.worldServers[0].getWorldInfo().areCommandsAllowed();
    }

    /** Every client tick. */
    public static void tick(Minecraft mc) {
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null || mc.thePlayer == null) { opened = null; return; }
        if (server != opened) { opened = server; agreed = false; }
        if (agreed) return;
        Boolean essential = WorldCheats.essential();
        if (essential == null) return;
        if (essential != own) {
            WorldCheats.essential(own);
            info(server).setAllowCommands(own);
        }
        agreed = true;
    }

    /** GuiShareToLan's Start LAN World: Allow Cheats keeps the host's cheats with the world, and the choice is kept for next time. */
    public static void lan(Minecraft mc, boolean cheats) {
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;
        WorldCheats.guests(folder(mc, server), cheats);
        if (!cheats) return;
        own = true;
        info(server).setAllowCommands(true);
        WorldCheats.essential(true);
    }

    /** The host's last Allow Cheats on the LAN screen for this world, or null. */
    public static Boolean lanChoice(Minecraft mc) {
        IntegratedServer server = mc.getIntegratedServer();
        return server == null ? null : WorldCheats.guests(folder(mc, server));
    }

    private static WorldInfo info(IntegratedServer server) { return server.worldServers[0].getWorldInfo(); }

    private static Path folder(Minecraft mc, IntegratedServer server) {
        return new File(WorldBackup189.savesDir(mc), server.getFolderName()).toPath();
    }
}
