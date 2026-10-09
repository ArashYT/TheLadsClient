package com.thelads.core.v1_20_1.game.nea.mixin;

import com.thelads.core.v1_20_1.game.nea.Nea1201;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Priority above the default: this return hook runs after other mods' setupAnim hooks, so their poses are the starting point.
@Mixin(value = PlayerModel.class, priority = 1100)
public class NeaPlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void lads$notEnoughAnimations(LivingEntity entity, float limbSwing, float limbAmount, float age, float headYaw, float headPitch,
            CallbackInfo callback) {
        Nea1201.afterSetupAnim((PlayerModel<?>) (Object) this, entity, limbSwing, limbAmount, age);
    }
}
