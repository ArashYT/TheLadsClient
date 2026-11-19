package com.thelads.ladsapi.paper.event;

import com.thelads.ladsapi.api.LadsPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when a Lads Client player presses or releases a server action offered with LadsPlayer#actions (PROTOCOL.md section 20). Only ids of the player's current list, at most 20 per second. */
public final class LadsActionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LadsPlayer ladsPlayer;
    private final String action;
    private final boolean down;

    public LadsActionEvent(Player player, LadsPlayer ladsPlayer, String action, boolean down) {
        this.player = player;
        this.ladsPlayer = ladsPlayer;
        this.action = action;
        this.down = down;
    }

    public Player getPlayer() {
        return player;
    }

    public LadsPlayer getLadsPlayer() {
        return ladsPlayer;
    }

    /** The id given to ServerAction. */
    public String getAction() {
        return action;
    }

    /** True on press, false on release. */
    public boolean isDown() {
        return down;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
