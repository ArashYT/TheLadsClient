package com.thelads.core.v1_21_11.mixin.chrome;

import com.thelads.core.v1_21_11.gui.LadsKeyBindsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Controls opens the embedded Controlling screen, or the native Lads search screen when Controlling (installed) swaps in its own. */
@Mixin(value = Minecraft.class, priority = 900)
public abstract class ControlsScreenMixin {
    @Shadow public Screen screen;

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 1)
    private Screen ladsControls(Screen next) {
        if (next != null && (next.getClass() == KeyBindsScreen.class
                || next.getClass().getName().equals("com.blamejared.controlling.client.NewKeyBindsScreen"))) {
            // Players get the embedded Controlling screen; the native search screen stays for an installed
            // Controlling jar, which keeps the embedded copy off.
            return com.thelads.core.v1_21_11.embedded.EmbeddedMods.active("controlling")
                ? new com.thelads.core.v1_21_11.embedded.controlling.client.NewKeyBindsScreen(screen, Minecraft.getInstance().options)
                : new LadsKeyBindsScreen(screen, Minecraft.getInstance().options);
        }
        return next;
    }
}
