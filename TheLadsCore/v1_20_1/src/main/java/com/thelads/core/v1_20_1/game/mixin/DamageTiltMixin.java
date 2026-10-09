package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.client.DamageTilt;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public class DamageTiltMixin {
    @Redirect(method = "bobHurt", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;getHurtDir()F"), require = 0)
    private float lads$direction(LivingEntity entity) {
        Module mod = ModuleManager.getInstance().getModule(DamageTilt.MODULE);
        if (mod == null || !mod.isEnabled()) return entity.getHurtDir();
        return DamageTilt.CLIENT.cameraYaw(mod, System.currentTimeMillis());
    }

    @Redirect(method = "bobHurt", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"), require = 0)
    private Object lads$intensity(OptionInstance<Double> instance) {
        Double base = instance.get();
        Module mod = ModuleManager.getInstance().getModule(DamageTilt.MODULE);
        if (mod == null || !mod.isEnabled() || base == null) return base;
        return base * DamageTilt.CLIENT.cameraStrength(mod, System.currentTimeMillis());
    }
}
