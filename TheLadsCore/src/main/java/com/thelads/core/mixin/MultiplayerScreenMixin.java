package com.thelads.core.mixin;

import com.thelads.core.client.auth.AccountSwitcherScreen;
import net.minecraft.client.Minecraft;
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

import java.util.ArrayList;
import java.util.List;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    @Shadow protected ServerSelectionList serverSelectionList;

    protected MultiplayerScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void ladsAddButtons(CallbackInfo ci) {
        // Strip the "Play Multiplayer" header text — the screen should be just
        // the panorama and the server list.
        List<GuiEventListener> titles = new ArrayList<>();
        for (GuiEventListener child : this.children()) {
            if (child instanceof StringWidget) titles.add(child);
        }
        titles.forEach(this::removeWidget);

        // Account switch button — top-left
        this.addRenderableWidget(Button.builder(
            Component.literal("⇄ Account"),
            btn -> Minecraft.getInstance().setScreenAndShow(new AccountSwitcherScreen((Screen)(Object) this))
        ).bounds(8, 6, 90, 20).build());

        // Copy IP button — top middle
        this.addRenderableWidget(Button.builder(
            Component.literal("Copy IP"),
            btn -> {
                ServerData selected = ladsGetSelected();
                if (selected != null && selected.ip != null) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(selected.ip);
                    btn.setMessage(Component.literal("Copied!"));
                }
            }
        ).bounds(this.width / 2 - 36, 6, 72, 20).build());
    }

    @Unique
    private ServerData ladsGetSelected() {
        if (this.serverSelectionList == null) return null;
        Object entry = this.serverSelectionList.getSelected();
        if (entry instanceof OnlineServerEntryAccessor acc) {
            return acc.ladsGetServerData();
        }
        return null;
    }
}
