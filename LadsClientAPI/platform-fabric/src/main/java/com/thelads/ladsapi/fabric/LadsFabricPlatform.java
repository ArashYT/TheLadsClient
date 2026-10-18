package com.thelads.ladsapi.fabric;

import com.thelads.ladsapi.platform.LadsPlatform;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class LadsFabricPlatform implements LadsPlatform {
    private static final Logger LOGGER = Logger.getLogger("LadsClientAPI");
    private final Path dataFolder;
    private final Map<UUID, LadsFabricPlayerHandle> players = new ConcurrentHashMap<>();
    private final Queue<ScheduledTask> scheduledTasks = new ConcurrentLinkedQueue<>();
    private volatile MinecraftServer server;

    private static class ScheduledTask {
        int ticksRemaining;
        final Runnable task;

        ScheduledTask(int ticksRemaining, Runnable task) {
            this.ticksRemaining = ticksRemaining;
            this.task = task;
        }
    }

    public LadsFabricPlatform() {
        this.dataFolder = FabricLoader.getInstance().getConfigDir().resolve("ladsclientapi");
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    private void onServerTick(MinecraftServer server) {
        this.server = server;
        if (scheduledTasks.isEmpty()) return;

        int size = scheduledTasks.size();
        for (int i = 0; i < size; i++) {
            ScheduledTask entry = scheduledTasks.poll();
            if (entry == null) break;
            entry.ticksRemaining--;
            if (entry.ticksRemaining <= 0) {
                try {
                    entry.task.run();
                } catch (Throwable t) {
                    LOGGER.severe("Error running scheduled task: " + t.getMessage());
                }
            } else {
                scheduledTasks.add(entry);
            }
        }
    }

    public LadsFabricPlayerHandle getOrCreatePlayer(ServerPlayer player) {
        return players.compute(player.getUUID(), (uuid, existing) -> new LadsFabricPlayerHandle(player));
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    @Override
    public String id() {
        return "fabric";
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
            if (server != null) {
                server.execute(task);
            } else {
                task.run();
            }
        } else {
            scheduledTasks.add(new ScheduledTask(ticks, task));
        }
    }

    @Override
    public Collection<? extends LadsPlayerHandle> online() {
        return players.values();
    }
}
