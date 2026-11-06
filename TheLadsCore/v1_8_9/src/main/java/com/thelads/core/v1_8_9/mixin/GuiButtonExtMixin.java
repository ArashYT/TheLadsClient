package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.gui.LadsButtonDraw189;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge's GuiButtonExt (and its GuiSlider, which the Lads' DynamicFps and Jade screens and mods' config screens use) overrides
 * drawButton, so it bypassed GuiButtonMixin: draw it the same way. Not remapped (a Forge class); the second name is the obfuscated one.
 */
@Mixin(value = GuiButtonExt.class, remap = false)
public abstract class GuiButtonExtMixin {
    @Inject(method = {"drawButton", "func_146112_a"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsDrawButton(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        ((LadsButtonDraw189) this).ladsDraw(mc, mouseX, mouseY);
        ci.cancel();
    }
}
