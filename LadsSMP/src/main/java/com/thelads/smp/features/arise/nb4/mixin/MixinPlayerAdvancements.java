package com.thelads.smp.features.arise.nb4.mixin;

import com.thelads.smp.features.arise.nb4.AriseNB4Config;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin hooking PlayerAdvancements to enforce Arise NB4 advancement rules:
 * - Allows disabling ladssmp advancements via config (advancementsEnabled = false)
 * - Applies advancementXpMultiplier bonus XP awards when custom advancements are achieved
 */
@Mixin(PlayerAdvancements.class)
public abstract class MixinPlayerAdvancements {
    @Shadow
    private ServerPlayer player;

    @Shadow
    public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

    @Inject(method = "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
    private void ladssmp$onAwardHead(AdvancementHolder advancement, String criterionKey, CallbackInfoReturnable<Boolean> cir) {
        if (!AriseNB4Config.get().advancementsEnabled) {
            if ("ladssmp".equals(advancement.id().getNamespace())) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void ladssmp$onGrantRewards(AdvancementHolder advancement, String criterionKey, CallbackInfoReturnable<Boolean> cir) {
        if (!"ladssmp".equals(advancement.id().getNamespace())) {
            return;
        }
        double multiplier = AriseNB4Config.get().advancementXpMultiplier;
        if (multiplier > 1.0 && player != null) {
            int baseExp = advancement.value().rewards().experience();
            if (baseExp > 0) {
                int bonus = (int) Math.round(baseExp * (multiplier - 1.0));
                if (bonus > 0) {
                    player.giveExperiencePoints(bonus);
                }
            }
        }
    }
}
