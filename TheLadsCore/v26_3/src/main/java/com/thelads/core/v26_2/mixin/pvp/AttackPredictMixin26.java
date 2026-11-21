package com.thelads.core.v26_2.mixin.pvp;

import com.thelads.core.v26_2.feature.PredictionFx26;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mace / Sword Optimizer: predicts the attack sound, smash effects and hurt flash. Visual/audio only; the attack packet is untouched. */
@Mixin(MultiPlayerGameMode.class)
public class AttackPredictMixin26 {
    @Inject(method = "attack", at = @At("HEAD"))
    private void lads$predictAttack(Player player, Entity target, CallbackInfo ci) {
        PredictionFx26.attack(player, target);
    }
}
