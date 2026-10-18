package com.thelads.smp.features.arise.nb2;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Hides player overhead nametags whenever solid blocks, walls, or terrain
 * obstruct direct line-of-sight between camera and target player (Realistic Nametag).
 */
public final class RealisticNametagManager {
    private RealisticNametagManager() {}

    public static boolean isRealisticNametagEnabled() {
        return NB2Config.get().realisticNametag;
    }

    @Environment(EnvType.CLIENT)
    public static boolean isObstructed(Vec3 cameraPos, Vec3 targetPos, boolean isSpectator) {
        if (!isRealisticNametagEnabled()) {
            return false;
        }

        // If target is in spectator mode and config says to disable in spectator
        if (isSpectator && NB2Config.get().realisticNametagSpectator) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        // Clip raycast from camera to nametag position
        ClipContext context = new ClipContext(
                cameraPos,
                targetPos,
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()
        );

        BlockHitResult hit = mc.level.clip(context);
        return hit.getType() != HitResult.Type.MISS;
    }
}
