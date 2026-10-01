// Adapted from Controlling 26.2.4 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.api.event;

import com.thelads.core.v26_2.embedded.controlling.api.events.IIsKeyCodeModifierEvent;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Fired to check if a {@link InputConstants.Key} is a valid key code modifier (like shift, control, alt).
 */
public record IsKeyCodeModifierEvent(InputConstants.Key key) implements IIsKeyCodeModifierEvent {

}
