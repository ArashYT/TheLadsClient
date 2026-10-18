package com.thelads.ladsapi.paper;

import com.thelads.ladsapi.LadsCore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens for Bukkit/Paper player join, quit, and world change events.
 */
public final class PaperEventListener implements Listener {
    private final LadsCore core;
    private final PaperPlatform platform;

    public PaperEventListener(LadsCore core, PaperPlatform platform) {
        this.core = core;
        this.platform = platform;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        core.join(platform.handleOf(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        core.quit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        core.worldChanged(platform.handleOf(event.getPlayer()));
    }
}
