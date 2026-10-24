package com.thelads.core.v1_8_9.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.modules.TimeChangerModule;
import com.thelads.core.modules.WeatherChangerModule;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Time Changer and Weather Changer on 1.8.9: the client world (isRemote; the integrated server's world is untouched) reports the
 * chosen time to the sun, sky and daylight (getCelestialAngle) and the chosen weather's rain and thunder strength. The real time and
 * weather are never written, so turning the module off shows the server's again at once.
 */
@Mixin(World.class)
public abstract class WorldTimeWeatherMixin189 {
    @Shadow @Final public boolean isRemote;
    @Shadow @Final public WorldProvider provider;
    @Shadow protected WorldInfo worldInfo;

    @ModifyReturnValue(method = "getCelestialAngle", at = @At("RETURN"), require = 1)
    private float lads$timeChanger(float vanilla, @Local(argsOnly = true) float partialTicks) {
        if (!isRemote) return vanilla;
        TimeChangerModule changer = TimeChangerModule.get();
        return changer != null && changer.isEnabled() ? provider.calculateCelestialAngle(changer.time(worldInfo.getWorldTime()), partialTicks) : vanilla;
    }

    @ModifyReturnValue(method = "getRainStrength", at = @At("RETURN"), require = 1)
    private float lads$rain(float vanilla) {
        int forced = forcedWeather();
        return forced < 0 ? vanilla : WeatherChangerModule.rain(forced);
    }

    @ModifyReturnValue(method = "getThunderStrength", at = @At("RETURN"), require = 1)
    private float lads$thunder(float vanilla) {
        int forced = forcedWeather();
        return forced < 0 ? vanilla : WeatherChangerModule.thunder(forced);
    }

    @Unique
    private int forcedWeather() {
        if (!isRemote) return -1;
        WeatherChangerModule changer = WeatherChangerModule.get();
        return changer == null ? -1 : changer.forced();
    }
}
