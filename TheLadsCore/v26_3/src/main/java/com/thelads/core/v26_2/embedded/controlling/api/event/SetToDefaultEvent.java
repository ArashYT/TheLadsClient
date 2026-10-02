// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.api.event;

import com.thelads.core.v26_2.embedded.controlling.api.events.ISetToDefaultEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

/**
 * Fired when a key is set to the default, either through the individual reset button or the global reset button.
 */
public record SetToDefaultEvent(Options options, KeyMapping mapping) implements ISetToDefaultEvent {

}
