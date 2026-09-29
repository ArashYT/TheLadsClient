// SPDX-License-Identifier: LGPL-3.0-only
// Layout integration adapted from AutoReconnect, Copyright 2023 Bstn1802, 2026 TerminalMC.
package com.thelads.core.v26_2.mixin.reconnect;

import com.thelads.core.v26_2.feature.NativeReconnect;
import com.thelads.core.v26_2.feature.ReconnectDialog;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DisconnectedScreen.class)
public abstract class ReconnectDisconnectedMixin extends Screen implements ReconnectDialog {
    @Shadow @Final @Mutable private Screen parent;
    @Shadow @Final private DisconnectionDetails details;
    @Shadow @Final @Mutable private LinearLayout layout;
    @Unique private boolean lads$controls;
    protected ReconnectDisconnectedMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("HEAD"))
    private void lads$freshLayout(CallbackInfo ci) {
        if (NativeReconnect.available()) { layout = LinearLayout.vertical(); lads$controls = false; }
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void lads$controls(CallbackInfo ci) {
        if (!NativeReconnect.canReconnect()) return;
        lads$controls = true;
        // Vanilla adds its back button last (after optional report/feedback controls).
        Button back = null;
        for (var child : children()) if (child instanceof Button button) back = button;
        if (NativeReconnect.local()) {
            parent = new SelectWorldScreen(new TitleScreen());
            if (back != null) back.setMessage(Component.translatable("gui.toWorld"));
        }
        int buttonWidth = back == null ? 200 : back.getWidth();
        Button retry = Button.builder(Component.literal("Reconnect"), button -> NativeReconnect.manual()).width(buttonWidth).build();
        Button cancel = Button.builder(Component.literal("Cancel reconnect"), button -> NativeReconnect.cancelCountdown()).width(buttonWidth).build();
        layout.addChild(retry); layout.addChild(cancel);
        layout.arrangeElements(); repositionElements(); clearWidgets(); layout.visitWidgets(this::addRenderableWidget);
        NativeReconnect.attach((DisconnectedScreen) (Object) this, details.reason(), retry, cancel);
    }

    @Inject(method = "shouldCloseOnEsc", at = @At("RETURN"), cancellable = true)
    private void lads$allowEscape(CallbackInfoReturnable<Boolean> cir) { if (lads$controls) cir.setReturnValue(true); }
    @Override public boolean lads$hasReconnectControls() { return lads$controls; }
    @Override public Screen lads$reconnectParent() { return parent; }
}
