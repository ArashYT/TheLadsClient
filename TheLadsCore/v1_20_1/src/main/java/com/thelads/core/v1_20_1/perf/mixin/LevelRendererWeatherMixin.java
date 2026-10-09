package com.thelads.core.v1_20_1.perf.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.ParticleCore;
import com.thelads.core.v1_20_1.feature.ParticleCore1201;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Weather culling: a rain/snow column wholly outside the view frustum is skipped before its precipitation type, light, random
 * source and quad are worked out. Vanilla walks every column within 10 blocks (441 with Fancy graphics) each frame; about two
 * thirds of them are behind or beside the camera. A skipped column draws nothing, a visible one is untouched, so the picture is
 * the same. The frustum is Particle Core's (set for this frame in {@code renderLevel}); without it (Particle Core culling off,
 * or weather drawn outside {@code renderLevel}) nothing is skipped. Off with {@code -Dlads.weathercull=false}.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererWeatherMixin {
    @WrapOperation(method = "renderSnowAndRain", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int ladsCullWeatherColumn(Level level, Heightmap.Types type, int x, int z, Operation<Integer> original) {
        Camera camera = ParticleCore1201.frameCamera;
        if (camera != null && ParticleCore.FRUSTUM.isValid()) {
            Vec3 eye = camera.getPosition();
            // The column's quads span x/z +-0.5 around the block centre and y floor(camY) +-10 (+-5 with Fast graphics):
            // radius 1 and half-height 11 enclose them.
            if (!ParticleCore.FRUSTUM.visibleCylinder(x + 0.5 - eye.x, Mth.floor(eye.y) - eye.y, z + 0.5 - eye.z, 1.0, 11.0)) {
                return Integer.MAX_VALUE; // above every rain band: vanilla's "from == to" check skips the column
            }
        }
        return original.call(level, type, x, z);
    }
}
