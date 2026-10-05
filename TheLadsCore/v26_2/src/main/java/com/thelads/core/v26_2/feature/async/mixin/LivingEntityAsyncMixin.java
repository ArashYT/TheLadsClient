package com.thelads.core.v26_2.feature.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.v26_2.feature.async.AsyncTicking;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
abstract class LivingEntityAsyncMixin {
    /** A death updates the killer's stats, advancements and scoreboard and rolls shared loot sequences. */
    @WrapMethod(method = "die")
    private void lads$lockDie(DamageSource source, Operation<Void> original) {
        AsyncTicking.locked(original, source);
    }

    /** Removal unregisters the entity's locator-bar waypoint from the level. */
    @WrapMethod(method = "onRemoval")
    private void lads$lockRemoval(Entity.RemovalReason reason, Operation<Void> original) {
        AsyncTicking.locked(original, reason);
    }

    @WrapMethod(method = "stopRiding")
    private void lads$lockStopRiding(Operation<Void> original) {
        AsyncTicking.locked(original);
    }

    /** A player's effects: an axolotl gives regeneration to a player up to 20 blocks away, so two workers can reach one player. */
    @WrapMethod(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z")
    private boolean lads$lockAddEffect(MobEffectInstance effect, Entity source, Operation<Boolean> original) {
        return AsyncTicking.locked(original, effect, source);
    }

    @WrapMethod(method = "removeEffect")
    private boolean lads$lockRemoveEffect(Holder<MobEffect> effect, Operation<Boolean> original) {
        return AsyncTicking.locked(original, effect);
    }
}
