/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.AbstractSelectionList$Entry
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.StringWidget
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
 *  net.minecraft.client.gui.screens.multiplayer.ServerSelectionList
 *  net.minecraft.client.multiplayer.ServerData
 *  net.minecraft.network.chat.Component
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import com.thelads.core.client.auth.AccountSwitcherScreen;
import com.thelads.core.mixin.OnlineServerEntryAccessor;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={JoinMultiplayerScreen.class})
public abstract class MultiplayerScreenMixin
extends Screen {
    @Shadow
    protected ServerSelectionList serverSelectionList;

    protected MultiplayerScreenMixin() {
        super((Component)Component.empty());
    }

    @Inject(method={"init"}, at={@At(value="TAIL")}, require=0)
    private void ladsAddButtons(CallbackInfo ci) {
        ArrayList<GuiEventListener> titles = new ArrayList<GuiEventListener>();
        for (GuiEventListener guiEventListener : this.children()) {
            if (!(guiEventListener instanceof StringWidget)) continue;
            titles.add(guiEventListener);
        }
        Object object = this;
        titles.forEach(x$0 -> object.removeWidget((GuiEventListener)x$0));
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u21c4 Account"), btn -> Minecraft.getInstance().setScreen((Screen)new AccountSwitcherScreen(this))).bounds(8, 6, 90, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Copy IP"), btn -> {
            ServerData selected = this.ladsGetSelected();
            if (selected != null && selected.ip != null) {
                Minecraft.getInstance().keyboardHandler.setClipboard(selected.ip);
                btn.setMessage((Component)Component.literal((String)"Copied!"));
            }
        }).bounds(this.width / 2 - 36, 6, 72, 20).build());
    }

    @Unique
    private ServerData ladsGetSelected() {
        if (this.serverSelectionList == null) {
            return null;
        }
        AbstractSelectionList.Entry entry = this.serverSelectionList.getSelected();
        if (entry instanceof OnlineServerEntryAccessor) {
            OnlineServerEntryAccessor acc = (OnlineServerEntryAccessor)entry;
            return acc.ladsGetServerData();
        }
        return null;
    }
}

