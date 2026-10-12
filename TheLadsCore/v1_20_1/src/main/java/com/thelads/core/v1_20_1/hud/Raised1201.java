package com.thelads.core.v1_20_1.hud;

import com.thelads.core.client.ChatLift;
import com.thelads.core.modules.RaisedModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

    /** GUI pixels chat is drawn, hovered and clicked higher: {@link #chat()}, or more to clear the survival HUD's armor row (ChatLift). */
    public static int chatLift() {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        int rows = player == null ? 1 : ChatLift.healthRows((float) player.getAttributeValue(Attributes.MAX_HEALTH), player.getHealth(), player.getAbsorptionAmount());
        return ChatLift.lift(chat(), minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer(), hotbar(), 40, rows);
    }
}
