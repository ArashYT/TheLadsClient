package com.thelads.core.v1_20_1.mixin.pvp;

import com.thelads.core.v1_20_1.feature.PredictionFx1201;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sword Optimizer: predicts the attack sound and hurt flash. Audio/visual only; the attack packet is untouched. */
@Mixin(MultiPlayerGameMode.class)
public class AttackPredictMixin1201 {
    @Inject(method = "attack", at = @At("HEAD"))
    private void lads$predictAttack(Player player, Entity target, CallbackInfo ci) {
        PredictionFx1201.attack(player, target);
    }
}
