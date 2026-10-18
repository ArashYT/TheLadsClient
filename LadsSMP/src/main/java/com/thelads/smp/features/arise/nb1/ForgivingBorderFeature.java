package com.thelads.smp.features.arise.nb1;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native clean-room implementation of Forgiving Border.
 * Gently teleports players safely inside the closest world border boundary
 * instead of inflicting continuous suffocation damage.
 */
public final class ForgivingBorderFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-ForgivingBorder");

    public static void init() {
        LOGGER.info("[Lads SMP] Forgiving Border initialized (bounceBuffer={} blocks)", AriseNB1Config.get().forgivingBorder.bounceBuffer);
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().forgivingBorder.enabled;
    }

    public static void handlePlayerOutsideBorder(ServerPlayer player) {
        if (!isEnabled()) return;

        WorldBorder border = player.level().getWorldBorder();
        if (border.isWithinBounds(player.getBoundingBox())) {
            return;
        }

        double buffer = AriseNB1Config.get().forgivingBorder.bounceBuffer;
        Vec3 clamped = border.clampVec3ToBound(player.getX(), player.getY(), player.getZ());

        // Inward nudge from the boundary
        double targetX = clamped.x;
        double targetZ = clamped.z;

        if (targetX >= border.getMaxX() - 0.5) targetX -= buffer;
        else if (targetX <= border.getMinX() + 0.5) targetX += buffer;

        if (targetZ >= border.getMaxZ() - 0.5) targetZ -= buffer;
        else if (targetZ <= border.getMinZ() + 0.5) targetZ += buffer;

        player.teleportTo(targetX, player.getY(), targetZ);
        player.setDeltaMovement(0, 0, 0);

        player.sendSystemMessage(
                Component.literal("Teleported safely inside the world border.").withStyle(ChatFormatting.GOLD)
        );
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.0F);
    }
}
