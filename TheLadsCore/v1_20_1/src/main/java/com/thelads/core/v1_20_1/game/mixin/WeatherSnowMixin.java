package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.modules.WeatherChangerModule;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Weather Changer on 1.20.1: what falls in each drawn column (renderSnowAndRain) and where rain splashes and sounds (tickRain) follows
 * WeatherChangerModule.precipitation: snow everywhere, rain in dry biomes too. Biome.Precipitation's ordinals are the module's FALLS_.
 */
@Mixin(LevelRenderer.class)
public abstract class WeatherSnowMixin {
    @Redirect(method = {"renderSnowAndRain", "tickRain"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"),
        require = 2)
    private Biome.Precipitation lads$weatherFalls(Biome biome, BlockPos pos) {
        Biome.Precipitation vanilla = biome.getPrecipitationAt(pos);
        WeatherChangerModule changer = WeatherChangerModule.get();
        int forced = changer == null ? -1 : changer.forced();
        if (forced < 0) return vanilla;
        int falls = WeatherChangerModule.precipitation(forced, vanilla.ordinal());
        return falls == WeatherChangerModule.FALLS_SNOW ? Biome.Precipitation.SNOW
            : falls == WeatherChangerModule.FALLS_RAIN ? Biome.Precipitation.RAIN : Biome.Precipitation.NONE;
    }
}
