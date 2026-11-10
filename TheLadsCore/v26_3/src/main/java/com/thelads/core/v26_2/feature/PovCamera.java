package com.thelads.core.v26_2.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/**
 * Server POV spectating: the server points our camera at another player while we stay out of spectator mode
 * and mirrors that player's hotbar, health and held item onto our own player. Vanilla would draw the HUD from
 * the camera player, whose inventory the client only knows the held item of, and would let our mouse sway the hand.
 */
public final class PovCamera {
    private PovCamera() {}

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        return mc.player != null && cam instanceof Player && cam != mc.player
            && mc.gameMode != null && mc.gameMode.getPlayerMode() != GameType.SPECTATOR;
    }
}
