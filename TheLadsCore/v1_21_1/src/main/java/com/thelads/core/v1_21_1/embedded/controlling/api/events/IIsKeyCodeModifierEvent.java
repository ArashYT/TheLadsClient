// Adapted from Controlling 19.0.5 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.controlling.api.events;

import com.mojang.blaze3d.platform.InputConstants;

public interface IIsKeyCodeModifierEvent {
    
    InputConstants.Key key();
    
}
