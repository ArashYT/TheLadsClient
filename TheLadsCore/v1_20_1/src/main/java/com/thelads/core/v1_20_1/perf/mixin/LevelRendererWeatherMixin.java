package com.thelads.core.v1_20_1.perf.mixin;

import com.thelads.core.v1_20_1.perf.WeatherOptimizer1201;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererWeatherMixin {

    @Shadow private int ticks;

    @Inject(
        method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
        at = @At("HEAD"),
        require = 0
    )
    private void lads$startWeatherFrame(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        WeatherOptimizer1201.startFrame(camX, camY, camZ, this.ticks);
    }

    @Inject(
        method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
        at = @At("RETURN"),
        require = 0
    )
    private void lads$endWeatherFrame(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        WeatherOptimizer1201.endFrame();
    }

    @Redirect(
        method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"),
        require = 0
    )
    private int lads$redirectGetHeight(Level level, Heightmap.Types type, int x, int z) {
        return WeatherOptimizer1201.getPrecipitationHeight(level, type, x, z);
    }
}
