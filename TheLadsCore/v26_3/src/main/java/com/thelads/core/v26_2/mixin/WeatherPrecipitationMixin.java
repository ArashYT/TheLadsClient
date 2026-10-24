package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.modules.WeatherChangerModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Weather Changer on 26.3: what falls in a loaded column (the weather renderer's columns and the rain splashes and sounds both ask
 * ClientLevel.getPrecipitationAt) follows WeatherChangerModule.precipitation: snow everywhere, rain in dry biomes too. Unloaded
 * chunks keep NONE. Biome.Precipitation's ordinals are the module's FALLS_.
 */
@Mixin(ClientLevel.class)
public abstract class WeatherPrecipitationMixin {
    @WrapOperation(method = "getPrecipitationAt", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;"),
        require = 1)
    private Biome.Precipitation lads$weatherFalls(Biome biome, BlockPos pos, int seaLevel, Operation<Biome.Precipitation> original) {
        Biome.Precipitation vanilla = original.call(biome, pos, seaLevel);
        WeatherChangerModule changer = WeatherChangerModule.get();
        int forced = changer == null ? -1 : changer.forced();
        if (forced < 0) return vanilla;
        int falls = WeatherChangerModule.precipitation(forced, vanilla.ordinal());
        return falls == WeatherChangerModule.FALLS_SNOW ? Biome.Precipitation.SNOW
            : falls == WeatherChangerModule.FALLS_RAIN ? Biome.Precipitation.RAIN : Biome.Precipitation.NONE;
    }
}
