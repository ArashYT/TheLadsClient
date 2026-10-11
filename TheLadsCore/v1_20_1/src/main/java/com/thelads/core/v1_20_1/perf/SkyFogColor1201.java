package com.thelads.core.v1_20_1.perf;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.perf.TickSampledColor;
import com.thelads.core.v1_20_1.perf.mixin.CameraEyeAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sky and fog colour: the biome blend (CubicSampler over 6x6x6 biome cells; twice a frame for the sky, once for the fog, about 4%
 * of the render thread in flight) is sampled once per game tick at the camera's tick positions and interpolated for the frames in
 * between ({@link TickSampledColor}). Only the blend is cached: time of day, rain, thunder, lightning, the dimension's effects,
 * Forge's fog event, Oculus and Distant Horizons all still apply per frame. Queries off the first-person camera path (third
 * person, other mods' positions) and off the render thread sample as before. Kill switch -Dlads.skyfogcache=false.
 */
public final class SkyFogColor1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1");
    private static final boolean ENABLED = !"false".equals(System.getProperty("lads.skyfogcache"));
    public static final TickSampledColor SKY = new TickSampledColor(), FOG = new TickSampledColor();
    private static final double[] OUT = new double[3];
    private static boolean announced;

    private SkyFogColor1201() {}

    /** The wrapped CubicSampler.gaussianSampleVec3(quart position, fetcher) of ClientLevel.getSkyColor or FogRenderer.setupColor. */
    public static Vec3 sample(TickSampledColor cache, Vec3 quart, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Camera camera = mc.gameRenderer.getMainCamera();
        Entity entity = camera.getEntity();
        if (!ENABLED || level == null || entity == null || !RenderSystem.isOnRenderThread()) return original.call(quart, fetcher);
        // Camera.setup (first person): lerp(old, current position) + lerp(eyeHeightOld, eyeHeight), in the sampler's quart units.
        CameraEyeAccessor eye = (CameraEyeAccessor) camera;
        boolean cached = cache.get(level.getGameTime(),
            (entity.xo - 2) * 0.25, (entity.yo + eye.lads$eyeHeightOld() - 2) * 0.25, (entity.zo - 2) * 0.25,
            (entity.getX() - 2) * 0.25, (entity.getY() + eye.lads$eyeHeight() - 2) * 0.25, (entity.getZ() - 2) * 0.25,
            quart.x, quart.y, quart.z, (x, y, z, rgb) -> {
                Vec3 color = original.call(new Vec3(x, y, z), fetcher);
                rgb[0] = color.x;
                rgb[1] = color.y;
                rgb[2] = color.z;
            }, OUT);
        if (!cached) return original.call(quart, fetcher);
        if (!announced) {
            announced = true;
            LOGGER.info("Lads sky/fog colour: biome blend sampled once per tick, interpolated per frame");
        }
        return new Vec3(OUT[0], OUT[1], OUT[2]);
    }
}
