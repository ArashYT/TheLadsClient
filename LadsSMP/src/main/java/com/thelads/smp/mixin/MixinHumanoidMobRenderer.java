package com.thelads.smp.mixin;

import com.thelads.smp.features.LeaderRenderStateExtension;
import com.thelads.smp.features.ZombieImprovementsManager;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Consolidates 'zombieimprovements': Copies leader zombie status from Zombie entity to HumanoidRenderState.
 */
@Mixin(HumanoidMobRenderer.class)
public abstract class MixinHumanoidMobRenderer {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;F)V",
            at = @At("HEAD")
    )
    private void ladssmp$extractLeaderRenderState(Mob entity, HumanoidRenderState state, float partialTicks, CallbackInfo ci) {
        if (entity instanceof Zombie) {
            ((LeaderRenderStateExtension) state).ladssmp$setLeader(ZombieImprovementsManager.isLeader(entity));
        }
    }
}
