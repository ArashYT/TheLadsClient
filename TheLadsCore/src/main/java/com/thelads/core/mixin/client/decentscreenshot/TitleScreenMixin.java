/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.gui.screens.TitleScreen
 *  net.minecraft.network.chat.Component
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.client.decentscreenshot;

import com.thelads.core.features.decentscreenshot.ScreenshotGalleryScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={TitleScreen.class})
public abstract class TitleScreenMixin
extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method={"init"}, at={@At(value="TAIL")})
    private void addScreenshotGalleryButton(CallbackInfo info) {
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Lads Client"), button -> this.minecraft.setScreenAndShow((Screen)new com.thelads.core.client.gui.LadsSettingsScreen(this))).pos(this.width - 106, this.height - 76).size(100, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Gallery"), button -> this.minecraft.setScreenAndShow((Screen)new ScreenshotGalleryScreen(this))).pos(this.width - 106, this.height - 52).size(100, 20).build());
    }
}

