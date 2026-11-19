package com.thelads.ladsapi.paper.event;

import com.thelads.ladsapi.api.LadsInput;
import com.thelads.ladsapi.api.LadsPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when a Lads Client player's input device is reported (PROTOCOL.md section 9): the first report after a hello, then when it changes, at most one per 4 seconds. Needs input-device: true in config.yml. */
public final class LadsInputDeviceEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LadsPlayer ladsPlayer;
    private final LadsInput.Device device;
    private final LadsInput.Pad pad;

    public LadsInputDeviceEvent(Player player, LadsPlayer ladsPlayer, LadsInput.Device device, LadsInput.Pad pad) {
        this.player = player;
        this.ladsPlayer = ladsPlayer;
        this.device = device;
        this.pad = pad;
    }

    public Player getPlayer() {
        return player;
    }

    public LadsPlayer getLadsPlayer() {
        return ladsPlayer;
    }

    public LadsInput.Device getDevice() {
        return device;
    }

    /** UNKNOWN unless the device is a gamepad. */
    public LadsInput.Pad getPad() {
        return pad;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
