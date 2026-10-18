package com.thelads.smp.features.arise.nb6.mixin;

import com.thelads.smp.features.arise.nb6.AriseNB6Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to Item superclass to provide bow-like hold-to-charge casting behavior for FishingRodItem.
 */
@Mixin(Item.class)
public abstract class MixinItem {

    @Inject(
            method = "getUseDuration(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ladssmp$getFishingRodUseDuration(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        AriseNB6Config config = AriseNB6Config.get();
        if (config.enabled && config.holdToCast && (Object) this instanceof FishingRodItem) {
            cir.setReturnValue(72000);
        }
    }

    @Inject(
            method = "getUseAnimation(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemUseAnimation;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ladssmp$getFishingRodUseAnimation(ItemStack stack, CallbackInfoReturnable<ItemUseAnimation> cir) {
        AriseNB6Config config = AriseNB6Config.get();
        if (config.enabled && config.holdToCast && (Object) this instanceof FishingRodItem) {
            cir.setReturnValue(ItemUseAnimation.BOW);
        }
    }

    @Inject(
            method = "releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ladssmp$releaseFishingRod(ItemStack stack, Level level, LivingEntity entity, int timeLeft, CallbackInfoReturnable<Boolean> cir) {
        AriseNB6Config config = AriseNB6Config.get();
        if (config.enabled && config.holdToCast && (Object) this instanceof FishingRodItem && entity instanceof Player player) {
            int chargeTicks = 72000 - timeLeft;
            if (chargeTicks < 2) {
                chargeTicks = 2;
            }

            float maxChargeTicks = Math.max(10.0f, config.maxCastChargeSeconds * 20.0f);
            float ratio = Math.min(1.0f, chargeTicks / maxChargeTicks);
            float minPower = config.minCastPower;
            float maxPower = config.maxCastPower;
            float power = minPower + (maxPower - minPower) * ratio;

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL,
                    0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));

            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                int lureBonus = (int) (EnchantmentHelper.getFishingTimeReduction(serverLevel, stack, player) * 20.0f);
                int luckBonus = EnchantmentHelper.getFishingLuckBonus(serverLevel, stack, player);

                FishingHook hook = new FishingHook(player, level, luckBonus, lureBonus);
                hook.setDeltaMovement(hook.getDeltaMovement().scale(power));
                Projectile.spawnProjectile(hook, serverLevel, stack);

                player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            }

            stack.causeUseVibration(player, GameEvent.ITEM_INTERACT_START);
            cir.setReturnValue(true);
        }
    }
}
