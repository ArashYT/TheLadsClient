/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.screens.DisconnectedScreen
 *  net.minecraft.network.chat.Component
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import com.thelads.core.client.AutoReconnect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={DisconnectedScreen.class})
public abstract class DisconnectedScreenMixin
extends Screen {
    @Shadow
    @Final
    private Screen parent;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method={"init"}, at={@At(value="TAIL")}, require=0)
    private void ladsAddReconnect(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        AutoReconnect.get().onScreenShown(this.parent, mc.getCurrentServer());
        int y = this.height - 30;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Reconnect"), b -> AutoReconnect.get().reconnect()).bounds(this.width / 2 - 154, y, 150, 20).build());
        if (AutoReconnect.get().isModuleEnabled()) {
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Cancel Auto-Reconnect"), b -> AutoReconnect.get().cancel()).bounds(this.width / 2 + 4, y, 150, 20).build());
        }
    }
}

