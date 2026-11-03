package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.auth.LadsAccount;
import com.thelads.core.client.auth.LiveSession;
import com.thelads.core.v1_8_9.feature.LocalSkins189;
import com.mojang.authlib.properties.PropertyMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.Session;

/** Minecraft's session swapped in the running game. */
public final class LiveSession189 implements LiveSession {
    /** Minecraft, by MinecraftSessionAccessor189. */
    public interface Game {
        PropertyMap lads$profileProperties();
        void lads$setSession(Session session);
    }

    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public String username() {
        return mc.getSession().getUsername();
    }

    @Override
    public String uuid() {
        return mc.getSession().getPlayerID();
    }

    @Override
    public boolean inWorld() {
        return mc.theWorld != null;
    }

    @Override
    public Runnable prepare(LadsAccount target, String accessToken) throws Exception {
        boolean offline = target.isOffline();
        Session session = new Session(target.getUsername(), target.sessionUuid().toString().replace("-", ""), accessToken, offline ? "legacy" : "mojang");
        // Nothing here asks Mojang by itself: a token it refuses throws here, before anything changed.
        if (!offline) mc.getSessionService().joinServer(session.getProfile(), accessToken, UUID.randomUUID().toString());
        return () -> {
            WorldClient world = mc.theWorld;
            if (world != null) { // as the pause menu's Save and Quit / Disconnect: a singleplayer world is saved before this returns
                world.sendQuittingDisconnectingPacket();
                mc.loadWorld(null);
                mc.displayGuiScreen(new GuiMainMenu());
            }
            Game game = (Game) mc;
            game.lads$setSession(session);
            game.lads$profileProperties().clear(); // Minecraft refills them for the new session when asked
            LocalSkins189.forgetAccount();
        };
    }
}
