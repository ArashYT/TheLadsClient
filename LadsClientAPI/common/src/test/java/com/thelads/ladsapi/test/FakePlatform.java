package com.thelads.ladsapi.test;

import com.thelads.ladsapi.platform.LadsPlatform;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

public class FakePlatform implements LadsPlatform {
    private final String id;
    private final boolean proxy;
    private final Path dataFolder;
    private final Logger logger = Logger.getLogger("FakePlatform");
    private final List<LadsPlayerHandle> onlinePlayers = new ArrayList<>();
    private final Map<Integer, List<Runnable>> scheduled = new TreeMap<>();
    private int currentTick = 0;

    public FakePlatform(String id, boolean proxy, Path dataFolder) {
        this.id = id;
        this.proxy = proxy;
        this.dataFolder = dataFolder;
    }

    @Override
    public String id() { return id; }

    @Override
    public boolean isProxy() { return proxy; }

    @Override
    public Path dataFolder() { return dataFolder; }

    @Override
    public Logger logger() { return logger; }

    @Override
    public void runLater(Runnable task, int ticks) {
        int targetTick = currentTick + Math.max(1, ticks);
        scheduled.computeIfAbsent(targetTick, k -> new ArrayList<>()).add(task);
    }

    @Override
    public Collection<? extends LadsPlayerHandle> online() {
        return onlinePlayers;
    }

    public void addPlayer(LadsPlayerHandle p) {
        onlinePlayers.add(p);
    }

    public void removePlayer(LadsPlayerHandle p) {
        onlinePlayers.remove(p);
    }

    public void advanceTicks(int ticks) {
        int endTick = currentTick + ticks;
        while (currentTick < endTick) {
            currentTick++;
            List<Runnable> tasks = scheduled.remove(currentTick);
            if (tasks != null) {
                for (Runnable r : tasks) {
                    r.run();
                }
            }
        }
    }

    public int getCurrentTick() {
        return currentTick;
    }
}
