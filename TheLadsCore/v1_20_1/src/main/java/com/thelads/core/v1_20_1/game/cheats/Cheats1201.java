package com.thelads.core.v1_20_1.game.cheats;

import com.thelads.core.client.WorldCheats;
import com.thelads.core.v1_20_1.game.mixin.LevelSettingsAccessor;
import com.thelads.core.v1_20_1.game.mixin.PrimaryLevelDataAccessor;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.slf4j.LoggerFactory;

/**
 * Cheats stay as the host set them (WorldCheats). When a world opens, joined players get the host's last choice and Essential's
 * switch takes the world's own; after that, Essential's Cheats switch changing is the host's choice, which the world takes and sends
 * to every player. Without Essential nothing but the joined players' choice is kept.
 */
public final class Cheats1201 {
    private static IntegratedServer opened;
    private static boolean agreed;
    private static int ticks;
    /** QA: what the last look found (Essential's switch, the world's own, whether they were brought together). */
    public static volatile String lastState = "not looked yet";

    private Cheats1201() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Cheats1201::tick);
    }

    static void tick(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) { opened = null; return; }
        if (server != opened) {
            opened = server;
            agreed = false;
            ticks = 0;
            Boolean guests = WorldCheats.guests(folder(server));
            if (guests != null) server.execute(() -> server.getPlayerList().setAllowCheatsForAllPlayers(guests));
        }
        if (agreed && ++ticks % 20 != 0) return; // after the world opened, Essential's switch is looked at once a second
        Boolean essential = WorldCheats.essential();
        if (essential == null) {
            lastState = "no Essential switch";
            return;
        }
        Boolean own = own(server);
        if (own == null) return;
        if (essential != own.booleanValue()) {
            boolean target = agreed ? essential : own;
            lastState = "Essential " + essential + ", world " + own + " -> " + target + (agreed ? " (the world follows Essential)" : " (Essential follows the world)");
            setWorldAllowCommands(server, target);
        } else lastState = "Essential and the world agree: " + own;
        agreed = true;
    }

    public static Path folder(IntegratedServer server) { return server.getWorldPath(LevelResource.ROOT); }

    /** The world's own switch (level.dat's), which Essential's answer for allowCommands() hides. */
    static Boolean own(IntegratedServer server) {
        return server.getWorldData() instanceof PrimaryLevelData data ? ((LevelSettingsAccessor) (Object) ((PrimaryLevelDataAccessor) data).ladsSettings()).ladsAllowCommands() : null;
    }

    /** The world's switch and Essential's both become {@code allow}, and every player is told what they may do. */
    static void setWorldAllowCommands(IntegratedServer server, boolean allow) {
        WorldCheats.essential(allow);
        if (!(server.getWorldData() instanceof PrimaryLevelData data)) return;
        server.execute(() -> {
            PrimaryLevelDataAccessor access = (PrimaryLevelDataAccessor) data;
            LevelSettings s = access.ladsSettings();
            access.ladsSettings(new LevelSettings(s.levelName(), s.gameType(), s.hardcore(), s.difficulty(), allow, s.gameRules(), s.getDataConfiguration()));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                server.getPlayerList().sendPlayerPermissionLevel(player);
                server.getCommands().sendCommands(player);
            }
            LoggerFactory.getLogger("TheLadsCore").info("Lads cheats: the world's cheats are now {}", allow ? "on" : "off");
        });
    }
}
