package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.modules.WeatherChangerModule;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Weather Changer on 26.3: the client world's rain and thunder levels are the chosen weather's (sky, rain columns, sounds and rain fog
 * follow them). The integrated server's levels keep their own weather.
 */
@Mixin(Level.class)
public abstract class WeatherLevelMixin {
    @ModifyReturnValue(method = "getRainLevel", at = @At("RETURN"), require = 1)
    private float lads$rain(float vanilla) {
        int forced = lads$forced();
        return forced < 0 ? vanilla : WeatherChangerModule.rain(forced);
    }

    @ModifyReturnValue(method = "getThunderLevel", at = @At("RETURN"), require = 1)
    private float lads$thunder(float vanilla) {
        int forced = lads$forced();
        return forced < 0 ? vanilla : WeatherChangerModule.thunder(forced);
    }

    @Unique
    private int lads$forced() {
        if (!((Level) (Object) this).isClientSide()) return -1;
        WeatherChangerModule changer = WeatherChangerModule.get();
        return changer == null ? -1 : changer.forced();
    }
}
