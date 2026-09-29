package com.thelads.core.v26_2.feature;

import net.minecraft.client.gui.screens.Screen;

/** Native disconnect controls own Escape only when their reconnect target was available. */
public interface ReconnectDialog {
    boolean lads$hasReconnectControls();
    Screen lads$reconnectParent();
}
