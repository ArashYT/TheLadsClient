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
            return new LadsKeyBindsScreen(screen, Minecraft.getInstance().options);
        }
        return next;
    }
}
