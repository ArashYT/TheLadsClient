package com.thelads.core.v1_20_1.lod;

import com.thelads.core.perf.PlayerLod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

/** Cut-off bits of the player being drawn right now (set around PlayerRenderer.render, read by the layer mixins). Render thread only. */
public final class PlayerLod1201 {
    public static int bits;

    private PlayerLod1201() {}

    public static void begin(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        // Only other players that really are in the world: never the local player, GUI paper dolls or the cosmetics preview.
        if (player == mc.player || mc.level == null || mc.level.getEntity(player.getId()) != player) { bits = 0; return; }
        PlayerLod.scoping = mc.player != null && mc.player.isScoping();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        bits = PlayerLod.bits(camera.distanceToSqr(player.getX(), player.getY(), player.getZ()));
    }

    public static boolean has(int bit) { return (bits & bit) != 0; }
}
