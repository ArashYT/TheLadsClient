package com.thelads.core.v1_20_1.game.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.DamageTilt;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.v1_20_1.game.Gameplay1201;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Damage tilt (the OldDamageTilt module, common DamageTilt), as 1.8.9's EntityRendererMixin: vanilla's timing and death roll stay. The
 * tilt turns by the hit's direction (Directional) or the old fixed way, and Intensity scales Minecraft's own Damage Tilt setting.
 */
@Mixin(GameRenderer.class)
public class DamageTiltMixin {
    @WrapOperation(method = "bobHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getHurtDir()F"))
    private float lads$direction(LivingEntity entity, Operation<Float> original) {
        Module mod = ModuleManager.getInstance().getModule(DamageTilt.MODULE);
        if (mod == null || !mod.isEnabled()) return original.call(entity);
        float yaw = DamageTilt.CLIENT.cameraYaw(mod, System.currentTimeMillis());
        Gameplay1201.lastTiltYaw = yaw;
        return yaw;
    }

    @WrapOperation(method = "bobHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object lads$intensity(OptionInstance<?> instance, Operation<Object> original) {
        Object base = original.call(instance);
        Module mod = ModuleManager.getInstance().getModule(DamageTilt.MODULE);
        if (mod == null || !mod.isEnabled() || !(base instanceof Double value)) return base;
        float strength = DamageTilt.CLIENT.cameraStrength(mod, System.currentTimeMillis());
        Gameplay1201.lastTiltStrength = strength;
        return value * strength;
    }
}
