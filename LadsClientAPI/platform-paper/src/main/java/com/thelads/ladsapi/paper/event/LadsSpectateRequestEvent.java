package com.thelads.ladsapi.paper.event;

import com.thelads.ladsapi.api.LadsPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a spectating Lads Client player presses the key for the next or previous target (PROTOCOL.md section 7). Only for
 * a player whose last {@link LadsPlayer#spectate} or {@link LadsPlayer#spectateFree} call allowed the switch. The client names
 * no target: the plugin that listens picks one and calls {@link LadsPlayer#spectate} again, or does nothing.
 */
public final class LadsSpectateRequestEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LadsPlayer ladsPlayer;
    private final boolean next;

    public LadsSpectateRequestEvent(Player player, LadsPlayer ladsPlayer, boolean next) {
        this.player = player;
        this.ladsPlayer = ladsPlayer;
        this.next = next;
    }

    public Player getPlayer() {
        return player;
    }

    public LadsPlayer getLadsPlayer() {
        return ladsPlayer;
    }

    /** True for the next target, false for the previous one. */
    public boolean isNext() {
        return next;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
