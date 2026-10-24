package com.thelads.ladsapi.velocity.event;

import com.thelads.ladsapi.api.LadsPlayer;
import com.velocitypowered.api.proxy.Player;

/**
 * Fired on Velocity when a Lads Client player completes the handshake in proxy-rules mode.
 */
public final class LadsPlayerReadyEvent {
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
}
