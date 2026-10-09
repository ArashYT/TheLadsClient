package com.thelads.core.v1_20_1.hud.overlay.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v1_20_1.hud.overlay.Nametags1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Nametags' Show Own Nametag in Third Person: only vanilla's "not the camera entity" exclusion is lifted. */
@Mixin(LivingEntityRenderer.class)
public class OwnNametagMixin {
    @WrapOperation(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getCameraEntity()Lnet/minecraft/world/entity/Entity;"))
    private Entity lads$ownName(Minecraft minecraft, Operation<Entity> original, @Local(argsOnly = true) LivingEntity entity) {
        return Nametags1201.cameraForNameRule(minecraft, entity, original.call(minecraft));
    }
}
