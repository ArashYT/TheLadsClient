package com.thelads.core.mixin.alwayson.betterrenderdistance.sodium;

import com.thelads.core.features.alwayson.betterrenderdistance.config.BRDConfig;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.collections.WriteQueue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={OcclusionCuller.class}, remap=false)
public abstract class SodiumOcclusionCullerMixin {
    @Shadow
    private Viewport viewport;

    @Unique
    private static double R2_XZ_BLOCKS = 0.0;
    @Unique
    private static double INV_R2_XZ = 0.0;
    @Unique
    private static double INV_R2_Y = 0.0;

    @Inject(method={"findVisible"}, at={@At(value="HEAD")})
    private void betterrenderdistance$beginFrame(CallbackInfo ci) {
        int radiusChunksV;
        double radiusChunksH;
        if (!BRDConfig.enabled) {
            return;
        }
        int vd = Minecraft.getInstance().options.getEffectiveRenderDistance();
        if (vd < 2) {
            vd = 2;
        }
        if ((radiusChunksH = (double)vd + 0.25) < 2.0) {
            radiusChunksH = 2.0;
        }
        double radiusBlocksH = radiusChunksH * 16.0;
        R2_XZ_BLOCKS = radiusBlocksH * radiusBlocksH;
        INV_R2_XZ = 1.0 / R2_XZ_BLOCKS;
        float vs = BRDConfig.verticalScale;
        if (!Float.isFinite(vs)) {
            vs = 0.5f;
        }
        if (vs < 0.05f) {
            vs = 0.05f;
        }
        if (vs > 2.0f) {
            vs = 2.0f;
        }
        if ((radiusChunksV = (int)Math.round((double)vd * (double)vs)) < 1) {
            radiusChunksV = 1;
        }
        double radiusBlocksV = ((double)radiusChunksV + 0.5) * 16.0;
        INV_R2_Y = 1.0 / (radiusBlocksV * radiusBlocksV);
    }

    @Inject(method={"tryVisitNode"}, at={@At(value="HEAD")}, cancellable=true)
    private void betterrenderdistance$cullEllipsoid(WriteQueue queue, RenderSection section, int directionsWide, int directionsRegular, int directionsLocal, RenderSection adjacentSection, int direction, CallbackInfo ci) {
        if (adjacentSection == null) {
            return;
        }
        if (!BRDConfig.enabled) {
            return;
        }
        CameraTransform camera = this.viewport.getTransform();
        double cx = (double)(adjacentSection.getChunkX() << 4) + 8.0;
        double dx = cx - camera.x;
        double cz = (double)(adjacentSection.getChunkZ() << 4) + 8.0;
        double dz = cz - camera.z;
        double d2xz = dx * dx + dz * dz;
        if (d2xz > R2_XZ_BLOCKS) {
            ci.cancel();
            return;
        }
        int oy = adjacentSection.getOriginY() - camera.intY;
        double dy = (double)SodiumOcclusionCullerMixin.nearestToZero(oy - 1, oy + 17) - (double)camera.fracY;
        double norm = d2xz * INV_R2_XZ + dy * dy * INV_R2_Y;
        if (norm > 1.0) {
            ci.cancel();
        }
    }

    @Unique
    private static int nearestToZero(int min, int max) {
        int clamped = 0;
        if (min > 0) {
            clamped = min;
        }
        if (max < 0) {
            clamped = max;
        }
        return clamped;
    }
}
