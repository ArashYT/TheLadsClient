// Adapted from Controlling 26.2.4 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.api.event;

import com.thelads.core.v26_2.embedded.controlling.api.events.ISetKeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

/**
 * Fired when a key is set.
 */
public record SetKeyEvent(Options options, KeyMapping mapping, InputConstants.Key key) implements ISetKeyEvent {

}
