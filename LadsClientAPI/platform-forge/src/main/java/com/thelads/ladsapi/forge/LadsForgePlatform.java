package com.thelads.ladsapi.forge;

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
    private final Queue<ScheduledTask> scheduledTasks = new ConcurrentLinkedQueue<>();

    private static class ScheduledTask {
        int ticksRemaining;
        final Runnable task;

        ScheduledTask(int ticksRemaining, Runnable task) {
            this.ticksRemaining = ticksRemaining;
            this.task = task;
        }
    }

    public LadsForgePlatform() {
        this.dataFolder = FMLPaths.CONFIGDIR.get().resolve("ladsclientapi");
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
            scheduledTasks.add(new ScheduledTask(ticks, task));
        }
    }

    @Override
    public Collection<? extends LadsPlayerHandle> online() {
        return players.values();
    }
}
