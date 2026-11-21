package com.thelads.core.v1_8_9.lod;

import com.thelads.core.perf.PlayerLod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;

/** Cut-off bits of the player being drawn right now (set around RenderPlayer.doRender, read by the layer mixins). Render thread only. */
public final class PlayerLod189 {
    public static int bits;

    private PlayerLod189() {}

    /** x, y, z are doRender's offsets from the camera. */
    public static void begin(AbstractClientPlayer player, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        // Only other players that really are in the world: never the local player, GUI paper dolls or the cosmetics preview.
        if (player == mc.thePlayer || mc.theWorld == null || mc.theWorld.getEntityByID(player.getEntityId()) != player) { bits = 0; return; }
        bits = PlayerLod.bits(x * x + y * y + z * z);
    }

    public static boolean has(int bit) { return (bits & bit) != 0; }
}
