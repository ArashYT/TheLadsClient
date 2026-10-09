package com.thelads.core.v1_20_1.hud;

import com.thelads.core.modules.RaisedModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Raised on 1.20.1 ({@link RaisedModule}): how far the vanilla hotbar group and action bar move up (RaisedMixin) and how far chat
 * does (the chat group's ChatComponent mixin reads {@link #chat()}). Stands down while the Raised mod is loaded.
 */
public final class Raised1201 {
    private Raised1201() {}

    private static boolean mod() {
        return Mods1201.loaded("raised");
    }

    /** GUI pixels the hotbar, the bars on it and the action bar move up now. */
    public static int hotbar() {
        RaisedModule raised = RaisedModule.get();
        return raised == null || mod() ? 0 : raised.hotbarLift(Minecraft.getInstance().screen instanceof ChatScreen);
    }

    /** GUI pixels chat moves up. */
    public static int chat() {
        RaisedModule raised = RaisedModule.get();
        return raised == null || mod() ? 0 : raised.chatLift();
    }
}
