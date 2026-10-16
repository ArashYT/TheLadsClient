package com.thelads.smp.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.features.LeaderRenderStateExtension;
import com.thelads.smp.features.ZombieImprovementsManager;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Consolidates 'zombieimprovements': Renders unique texture for Leader Zombies when enabled.
 */
@Mixin(AbstractZombieRenderer.class)
public abstract class MixinAbstractZombieRenderer {

    @ModifyReturnValue(
            method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("RETURN")
    )
    private <S extends ZombieRenderState> Identifier ladssmp$modifyLeaderTexture(
            Identifier original,
            @Local(argsOnly = true) S zombie
    ) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        if (cfg.zombieImprovements && cfg.zombieLeaderUniqueTexture && ((LeaderRenderStateExtension) zombie).ladssmp$isLeader()) {
            return ZombieImprovementsManager.id("textures/entity/zombie/leader_zombie.png");
        }
        return original;
    }
}
