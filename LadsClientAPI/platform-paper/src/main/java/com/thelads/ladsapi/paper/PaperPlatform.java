package com.thelads.ladsapi.paper;

import com.thelads.ladsapi.platform.LadsPlatform;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/**
 * Paper implementation of LadsPlatform.
 */
public final class PaperPlatform implements LadsPlatform {
    private final JavaPlugin plugin;

    public PaperPlatform(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "paper";
    }

    @Override
    public boolean isProxy() {
        return false;
    }

    @Override
    public Path dataFolder() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public Logger logger() {
        return plugin.getLogger();
    }

    @Override
    public void runLater(Runnable task, int ticks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, task, Math.max(1, ticks));
    }

    @Override
    public Collection<? extends LadsPlayerHandle> online() {
        List<LadsPlayerHandle> list = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            list.add(new PaperPlayerHandle(plugin, p));
        }
        return list;
    }

    public PaperPlayerHandle handleOf(Player player) {
        return new PaperPlayerHandle(plugin, player);
    }
}
