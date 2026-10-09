package com.thelads.core.v1_20_1.embedded.controlling.mixin;

import com.thelads.core.v1_20_1.embedded.controlling.ControllingInit1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Players get Controlling's key binds screen wherever vanilla's would open (the Controls screen's "Key Binds" button). */
@Mixin(value = Minecraft.class, priority = 900)
public abstract class ControllingScreenMixin {
    @Shadow public Screen screen;

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 1)
    private Screen lads$controllingScreen(Screen next) {
        if (next != null && next.getClass() == KeyBindsScreen.class) {
            return ControllingInit1201.createScreen(this.screen, Minecraft.getInstance().options);
        }
        return next;
    }
}
