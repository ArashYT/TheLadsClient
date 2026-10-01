// Adapted from Controlling 29.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.controlling.api.events;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

public interface ISetKeyEvent {
    
    Options options();
    
    KeyMapping mapping();
    
    InputConstants.Key key();
    
}
