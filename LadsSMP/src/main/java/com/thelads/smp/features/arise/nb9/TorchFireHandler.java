package com.thelads.smp.features.arise.nb9;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Handles setting attacked entities on fire when striking with a held torch.
 */
public final class TorchFireHandler {

    public static InteractionResult handleAttack(
            Player player,
            Level level,
            InteractionHand hand,
            Entity target,
            EntityHitResult hitResult
    ) {
        AriseNB9Config config = AriseNB9Config.get();
        if (!config.torchFire) {
            return InteractionResult.PASS;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(Items.TORCH) || mainHand.is(Items.SOUL_TORCH)) {
            if (!level.isClientSide()) {
                target.igniteForSeconds(config.torchFireDurationSeconds);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 1.2F);
            }
        }

        return InteractionResult.PASS;
    }
}
