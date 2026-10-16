package com.thelads.core.v26_2.mixin;

import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v26_2.feature.survivaltweaks.NativeSurvivalTweaks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class SurvivalElytraSwimMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void lads$cancelElytraFlyingInLiquid(CallbackInfo ci) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (!NativeSurvivalTweaks.active() || mod == null || !mod.isElytraWaterSwimEnabled()) {
            return;
        }
        Player self = (Player) (Object) this;
        if (self.isFallFlying() && self.isInLiquid() && self.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER)) {
            self.stopFallFlying();
            self.setSwimming(true);
        }
    }
}
