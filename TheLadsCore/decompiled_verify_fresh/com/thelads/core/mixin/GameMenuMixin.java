/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.components.AbstractWidget
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.PauseScreen
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.ComponentContents
 *  net.minecraft.network.chat.contents.TranslatableContents
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import com.thelads.core.client.gui.GalleryScreen;
import com.thelads.core.client.gui.LadsSettingsScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={PauseScreen.class})
public abstract class GameMenuMixin
extends Screen {
    protected GameMenuMixin(Component title) {
        super(title);
    }

    @Inject(method={"init"}, at={@At(value="RETURN")}, require=0)
    private void addLadsSettingsButton(CallbackInfo ci) {
        AbstractWidget anchor = this.ladsFindDisconnectButton();
        int rowW = anchor != null ? anchor.getWidth() : 204;
        int x = anchor != null ? anchor.getX() : this.width / 2 - rowW / 2;
        int y = anchor != null ? anchor.getY() + anchor.getHeight() + 4 : this.height - 48;
        int half = (rowW - 4) / 2;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Lads Client"), button -> this.minecraft.setScreen((Screen)new LadsSettingsScreen(this))).bounds(x, y, half, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Gallery"), button -> this.minecraft.setScreen((Screen)new GalleryScreen(this))).bounds(x + half + 4, y, rowW - half - 4, 20).build());
    }

    @Unique
    private AbstractWidget ladsFindDisconnectButton() {
        AbstractWidget best = null;
        for (GuiEventListener guiEventListener : this.children()) {
            String string;
            if (!(guiEventListener instanceof AbstractWidget)) continue;
            AbstractWidget w = (AbstractWidget)guiEventListener;
            ComponentContents componentContents = w.getMessage().getContents();
            if (componentContents instanceof TranslatableContents) {
                TranslatableContents tc = (TranslatableContents)componentContents;
                string = tc.getKey();
            } else {
                string = "";
            }
            String key = string;
            String text = w.getMessage().getString();
            if (!key.equals("menu.disconnect") && !key.equals("menu.returnToTitle") && !text.equalsIgnoreCase("Disconnect") && !text.contains("Save and Quit") || best != null && w.getY() <= best.getY()) continue;
            best = w;
        }
        return best;
    }
}

