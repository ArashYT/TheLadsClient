package com.thelads.core.v1_20_1.game.misc.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Quark's Back Button Keybind (on by default, Mouse 4, Zero SMP) presses Back/Done or closes the screen after any screen took a click
 * with its button: with a hotbar slot (or Swap Item With Offhand) also on Mouse 4, every swap in an inventory closed the inventory.
 * In container screens a button bound to a hotbar slot or to the offhand swap is that bind's, not Quark's. Without Quark: nothing.
 */
@Pseudo
@Mixin(targets = "org.violetmoon.quark.content.client.module.BackButtonKeybindModule$Client", remap = false)
public abstract class QuarkBackButtonMixin {
    @Inject(method = "onMouseInput", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lads$hotbarFirst(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return;
        int button = ((MouseAccess1201) mc.mouseHandler).lads$activeButton();
        if (mc.options.keySwapOffhand.matchesMouse(button)) { ci.cancel(); return; }
        for (KeyMapping hotbar : mc.options.keyHotbarSlots) if (hotbar.matchesMouse(button)) { ci.cancel(); return; }
    }
}
