package com.thelads.core.v1_21_1.mixin.chrome;

import com.thelads.core.v1_21_1.gui.LadsKeyBindsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Controls always opens the native Lads search screen, also when Controlling (still installed) swaps in its own. */
@Mixin(value = Minecraft.class, priority = 900)
public abstract class ControlsScreenMixin {
    @Shadow public Screen screen;

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 1)
    private Screen ladsControls(Screen next) {
        if (next != null && (next.getClass() == KeyBindsScreen.class
                || next.getClass().getName().equals("com.blamejared.controlling.client.NewKeyBindsScreen"))) {
            return new LadsKeyBindsScreen(screen, Minecraft.getInstance().options);
        }
        return next;
    }
}
