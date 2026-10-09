// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core, 1.20.1 categories are plain strings.
package com.thelads.core.v1_20_1.embedded.controlling.api.entries;

import net.minecraft.network.chat.Component;

public interface ICategoryEntry {

    /** The category's translation key, as {@code KeyMapping#getCategory} returns it. */
    String category();

    Component label();

}
