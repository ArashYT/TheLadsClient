package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.gui.LadsKeyBindsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = Gui.class, priority = 900)
public abstract class ControlsScreenMixin {
    @Shadow private Screen screen;

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 1)
    private Screen ladsControls(Screen next) {
        if (next != null && (next.getClass() == KeyBindsScreen.class
                || next.getClass().getName().equals("com.blamejared.controlling.client.NewKeyBindsScreen"))) {
            // Players get the embedded Controlling screen; the native search screen stays for an installed
            // Controlling jar, which keeps the embedded copy off.
            return com.thelads.core.v26_2.embedded.EmbeddedMods.active("controlling")
                ? new com.thelads.core.v26_2.embedded.controlling.client.NewKeyBindsScreen(screen, Minecraft.getInstance().options)
                : new LadsKeyBindsScreen(screen, Minecraft.getInstance().options);
        }
        return next;
    }
}
