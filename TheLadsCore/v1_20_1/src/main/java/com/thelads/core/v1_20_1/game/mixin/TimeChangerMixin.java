package com.thelads.core.v1_20_1.game.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.modules.TimeChangerModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LevelTimeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Time Changer on 1.20.1: the client world's time of day (sun and moon angle, sky and star brightness, daylight: every reader goes
 * through LevelTimeAccess.getTimeOfDay) is the chosen time on the real day. The integrated server's levels and the stored time stay.
 */
@Mixin(LevelTimeAccess.class)
public interface TimeChangerMixin {
    @ModifyReturnValue(method = "getTimeOfDay", at = @At("RETURN"))
    private float lads$timeChanger(float vanilla) {
        if (!((Object) this instanceof ClientLevel level)) return vanilla;
        TimeChangerModule changer = TimeChangerModule.get();
        return changer != null && changer.isEnabled() ? level.dimensionType().timeOfDay(changer.time(level.dayTime())) : vanilla;
    }
}
