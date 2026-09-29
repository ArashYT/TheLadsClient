// SPDX-License-Identifier: LGPL-3.0-only
// API hook adapted from AutoReconnect, Copyright 2023 Bstn1802, 2026 TerminalMC.
package com.thelads.core.v26_2.mixin.reconnect;

import com.thelads.core.v26_2.feature.NativeReconnect;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class ReconnectGuiMixin {
    @Shadow private Screen screen;
    @Inject(method = "setScreen", at = @At(value = "FIELD", opcode = 181,
        target = "Lnet/minecraft/client/gui/Gui;screen:Lnet/minecraft/client/gui/screens/Screen;"))
    private void lads$screenChanged(Screen next, CallbackInfo ci) { NativeReconnect.screenChanged(screen, next); }
}
