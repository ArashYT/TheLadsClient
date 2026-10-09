// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core, 1.20.1 list API.
package com.thelads.core.v1_20_1.embedded.controlling.client;

import net.minecraft.client.gui.components.ContainerObjectSelectionList;

/** A row of the Controlling key lists: a category heading, a key binding, an available key or a list header. */
public abstract class KeyListEntry extends ContainerObjectSelectionList.Entry<KeyListEntry> {

    /** Brings the row's buttons and markers up to date with the key bindings (after any key changed). */
    public abstract void refreshEntry();

}
