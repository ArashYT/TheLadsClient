package com.thelads.smp.features.arise.nb7.mixin;

import com.thelads.smp.features.arise.nb7.BiomeCreeperEntity;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts Creeper#explodeCreeper() to delegate to BiomeCreeperEntity#customExplode().
 *
 * Mixin target: net.minecraft.world.entity.monster.Creeper
 * Method: private void explodeCreeper();
 * Bytecode verified with javap on net/minecraft/world/entity/monster/Creeper.class (26.3).
 */
@Mixin(Creeper.class)
public abstract class MixinCreeperExplode {

    @Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
    private void ladssmp$onExplodeCreeper(CallbackInfo ci) {
        if ((Object) this instanceof BiomeCreeperEntity biomeCreeper) {
            biomeCreeper.customExplode();
            ci.cancel();
        }
    }
}
