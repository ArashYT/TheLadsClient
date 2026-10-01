package com.thelads.core.v26_2.embedded.emf.mixin.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v26_2.embedded.emf.EMF;
import com.thelads.core.v26_2.embedded.emf.EMFManager;

@Mixin(Player.class)
public abstract class MixinPlayerEntity {

    @Inject(method = "interactOn", at = @At("HEAD"))
    private void emf$injected(CallbackInfoReturnable<InteractionResult> cir, @Local(argsOnly = true) Entity entity) {
        if (EMF.config().getConfig().debugOnRightClick && ((LivingEntity) ((Object) this)).level().isClientSide()) {
            EMFManager.getInstance().entityForDebugPrint = entity.getUUID();
        }
    }

}
