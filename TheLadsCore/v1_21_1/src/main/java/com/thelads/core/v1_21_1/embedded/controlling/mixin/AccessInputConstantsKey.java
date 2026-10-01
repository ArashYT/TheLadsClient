// Adapted from Controlling 19.0.5 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.controlling.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(InputConstants.Key.class)
public interface AccessInputConstantsKey {
    
    @Accessor("NAME_MAP")
    static Map<String, InputConstants.Key> controlling$getNAME_MAP() {
        
        throw new AssertionError();
    }
    
}
