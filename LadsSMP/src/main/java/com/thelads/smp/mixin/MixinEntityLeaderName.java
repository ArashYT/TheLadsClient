package com.thelads.smp.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.features.ZombieImprovementsManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Consolidates 'zombieimprovements': Prepends 'Leader ' to the entity type name of Leader Zombies.
 */
@Mixin(Entity.class)
public abstract class MixinEntityLeaderName {

    @ModifyReturnValue(method = "getTypeName", at = @At("RETURN"))
    private Component ladssmp$addLeaderToName(Component original) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        if (cfg.zombieImprovements && cfg.zombieLeaderNamePrefix && ZombieImprovementsManager.isLeader((Entity) (Object) this)) {
            return Component.translatable("entity.zombieimprovements.leader", original);
        }
        return original;
    }
}
