package com.thelads.ladsapi.paper.event;

import com.thelads.ladsapi.api.LadsPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player's client proved itself through the Lads relay (PROTOCOL.md section 6): a few seconds after
 * LadsPlayerReadyEvent, and never for a client older than 1.13.9 or a program that only imitates the hello.
 */
public final class LadsPlayerVerifiedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LadsPlayer ladsPlayer;

    public LadsPlayerVerifiedEvent(Player player, LadsPlayer ladsPlayer) {
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
