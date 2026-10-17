package com.thelads.core.v1_20_1.game.survival.mixin;

import com.thelads.core.v1_20_1.game.survival.NativeSurvivalTweaks1201;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** SurvivalTweaks: Boat 360 View. Removes the yaw clamp while a player is in a boat, allowing full 360-degree camera rotation. */
@Mixin(Boat.class)
public abstract class SurvivalBoat360Mixin1201 {
    @Redirect(
        method = "clampRotation",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F")
    )
    private float lads$boat360Clamp(float value, float min, float max, Entity passenger) {
        if (NativeSurvivalTweaks1201.isBoat360ViewEnabled() && passenger instanceof Player) {
            float clamped = Mth.clamp(value, min, max);
            if (clamped != value) {
                passenger.setYBodyRot(passenger.getViewYRot(1.0F) - clamped);
            }
            return value;
        }
        return Mth.clamp(value, min, max);
    }
}
