package com.thelads.core.v26_2.lod;

import com.thelads.core.perf.PlayerLod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Cut flags stored on a render state during extraction (Player LOD bits). */
public interface LodFlags26 {
    int lads$lod();
    void lads$lod(int bits);

    static boolean has(Object state, int bit) {
        return state instanceof LodFlags26 f && (f.lads$lod() & bit) != 0;
    }

    /** Player LOD bits for another player really in the world, measured from the main camera (so shadow passes do not matter). */
    static int avatarBits(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player || mc.level == null || mc.level.getEntity(entity.getId()) != entity) return 0;
        PlayerLod.scoping = mc.player != null && mc.player.isScoping();
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        return PlayerLod.bits(camera.distanceToSqr(entity.getX(), entity.getY(), entity.getZ()));
    }
}
