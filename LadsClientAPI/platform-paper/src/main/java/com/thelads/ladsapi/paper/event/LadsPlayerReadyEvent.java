package com.thelads.ladsapi.paper.event;

import com.thelads.ladsapi.api.LadsPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a Lads Client player connects and completes the protocol handshake.
 */
public final class LadsPlayerReadyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LadsPlayer ladsPlayer;

    public LadsPlayerReadyEvent(Player player, LadsPlayer ladsPlayer) {
        this.player = player;
        this.ladsPlayer = ladsPlayer;
    }

    public Player getPlayer() {
        return player;
    }

    public LadsPlayer getLadsPlayer() {
        return ladsPlayer;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
