// Adapted from Controlling 19.0.5 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.controlling.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBindsScreen.class)
public interface AccessKeyBindsScreen {
    
    @Accessor("keyBindsList")
    KeyBindsList controlling$getKeyBindsList();
    
    @Accessor("keyBindsList")
    void controlling$setKeyBindsList(KeyBindsList newList);
    
    @Accessor("resetButton")
    Button controlling$getResetButton();
    
    @Accessor("resetButton")
    void controlling$setResetButton(Button resetButton);
    
}
