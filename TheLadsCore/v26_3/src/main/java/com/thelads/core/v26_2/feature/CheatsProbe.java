package com.thelads.core.v26_2.feature;

import com.thelads.core.client.WorldCheats;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-cheats" from the harness's LADS_VERIFY_CHEATS): "set" turns cheats on the way World
 * Options does (Allow Commands, Guest Command Access), "off" turns both off, and
 * "check" reports, after the world reopened, what stayed: level.dat as saved, the world's switch, Essential's, the
 * host's permission and joined players' commands.
 */
final class CheatsProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static String phase;
    private static long due;
    private static boolean acted, done;
    private CheatsProbe() {}

    static void tick(Path game, boolean ready) {
        if (done || !ready) return;
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        try {
            if (phase == null) {
                Path request = game.resolve(".lads-qa-cheats");
                if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
                phase = Files.readString(request).trim();
                Files.delete(request);
                // "world switch" is level.dat's Allow Commands as the world loaded it (Essential answers over it, see WorldCheats).
                LOGGER.info("Lads cheats probe BEGIN: {}; at open: {}", phase, state(mc, server));
                due = System.nanoTime() + 6_000_000_000L; // Essential puts its switch on the world a moment after it opens
                return;
            }
            if (System.nanoTime() < due) return;
            boolean on = !phase.equals("off");
            if (!phase.equals("check") && !acted) {
                LOGGER.info("Lads cheats probe: before turning cheats {}: {}", on ? "on" : "off", state(mc, server));
                server.setWorldAllowCommands(on);              // World Options: Allow Commands
                server.setGuestCommandAccess(on);              // World Options: Guest Command Access
                acted = true;
                due = System.nanoTime() + 2_000_000_000L;
                return;
            }
            done = true;
            Boolean essential = WorldCheats.essential();
            boolean pass = server.getWorldData().isAllowCommands() == on && NativeCheats.own(server) == on
                && mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) == on
                && server.getGuestCommandAccess() == on && (essential == null || essential == on);
            String line = phase + "; " + state(mc, server);
            if (pass) LOGGER.info("Lads cheats probe END: 1 passed, 0 failed; {}", line);
            else LOGGER.error("Lads cheats probe FAILED: cheats did not stay {}; {}", on ? "on" : "off", line);
        } catch (Exception failure) {
            done = true;
            LOGGER.error("Lads cheats probe FAILED", failure);
        }
    }

    private static String state(Minecraft mc, IntegratedServer server) {
        return "world switch " + NativeCheats.own(server) + ", answered " + server.getWorldData().isAllowCommands()
            + ", Essential " + WorldCheats.essential() + ", host commands " + mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
            + ", other players " + server.getGuestCommandAccess()
            + ", kept choice for them " + WorldCheats.guests(server.getWorldPath(LevelResource.ROOT));
    }
}
