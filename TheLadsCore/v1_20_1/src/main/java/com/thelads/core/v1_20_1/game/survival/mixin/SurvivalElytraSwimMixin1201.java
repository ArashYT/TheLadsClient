package com.thelads.core.v1_20_1.game.survival.mixin;

import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v1_20_1.game.survival.NativeSurvivalTweaks1201;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SurvivalTweaks: Elytra water swim. Gliding into water or lava ends the glide and starts swimming. */
@Mixin(Player.class)
public abstract class SurvivalElytraSwimMixin1201 {
    @Inject(method = "tick", at = @At("HEAD"))
    private void lads$cancelElytraFlyingInLiquid(CallbackInfo ci) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (!NativeSurvivalTweaks1201.active() || mod == null || !mod.isElytraWaterSwimEnabled()) {
            return;
        }
        Player self = (Player) (Object) this;
        if (self.isFallFlying() && (self.isInWater() || self.isInLava()) && self.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            self.stopFallFlying();
            self.setSwimming(true);
        }
    }
}
