package com.thelads.ladsapi.forge;

import com.thelads.ladsapi.platform.LadsPlatform;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

public class LadsForgePlatform implements LadsPlatform {
    private static final Logger LOGGER = Logger.getLogger("LadsClientAPI");
    private final Path dataFolder;
    private final Map<UUID, LadsForgePlayerHandle> players = new ConcurrentHashMap<>();

    private final com.thelads.ladsapi.util.TickScheduler scheduler = new com.thelads.ladsapi.util.TickScheduler(LOGGER);

    public LadsForgePlatform() {
        this.dataFolder = FMLPaths.CONFIGDIR.get().resolve("ladsclientapi");
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        scheduler.tick();
    }

    public LadsForgePlayerHandle getOrCreatePlayer(ServerPlayer player) {
        UUID uuid = ForgeReflection.getPlayerUUID(player);
        if (uuid == null) uuid = player.getUUID();
        return players.compute(uuid, (k, existing) -> new LadsForgePlayerHandle(player));
    }

    public void removePlayer(UUID uuid) {
        if (uuid != null) {
            players.remove(uuid);
        }
    }

    @Override
    public String id() {
        return "forge";
    }

    @Override
    public boolean isProxy() {
        return false;
    }

    @Override
    public Path dataFolder() {
        return dataFolder;
    }

    @Override
    public Logger logger() {
        return LOGGER;
    }

    @Override
    public void runLater(Runnable task, int ticks) {
        if (ticks <= 0) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(task);
            } else {
                task.run();
            }
        } else {
            scheduler.runLater(task, ticks);
        }
    }

    @Override
    public Collection<? extends LadsPlayerHandle> online() {
        return players.values();
    }
}
