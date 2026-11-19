package com.thelads.ladsapi.paper.event;

import com.thelads.ladsapi.api.LadsPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when a dialog opened with LadsPlayer#dialog ends (PROTOCOL.md section 21): the choice is a button id, "dismiss" (Esc or closed) or "timeout" (nobody answered in time). Once per dialog. */
public final class LadsDialogResponseEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LadsPlayer ladsPlayer;
    private final String dialogId;
    private final String choice;

    public LadsDialogResponseEvent(Player player, LadsPlayer ladsPlayer, String dialogId, String choice) {
        this.player = player;
        this.ladsPlayer = ladsPlayer;
        this.dialogId = dialogId;
        this.choice = choice;
    }

    public Player getPlayer() {
        return player;
    }

    public LadsPlayer getLadsPlayer() {
        return ladsPlayer;
    }

    public String getDialogId() {
        return dialogId;
    }

    public String getChoice() {
        return choice;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
