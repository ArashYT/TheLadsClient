/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.culling.Frustum
 *  net.minecraft.client.renderer.entity.EntityRenderDispatcher
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.phys.AABB
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.thelads.core.mixin.alwayson.betterrenderdistance;

import com.thelads.core.features.alwayson.betterrenderdistance.config.BRDConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={EntityRenderDispatcher.class})
public abstract class EntityRenderDispatcherCullMixin {
    @Unique
    private static int CACHED_VD = Integer.MIN_VALUE;
    @Unique
    private static int CACHED_VS_BITS = Integer.MIN_VALUE;
    @Unique
    private static long CACHED_ENTITY_SCALE_BITS = Long.MIN_VALUE;
    @Unique
    private static double R2_XZ_BLOCKS = 0.0;
    @Unique
    private static double INV_R2_XZ = 0.0;
    @Unique
    private static double INV_R2_Y = 0.0;

    @Unique
    private static void updateCacheIfNeeded(Minecraft mc) {
        float vs;
        int vd = mc.options.getEffectiveRenderDistance();
        if (vd < 2) {
            vd = 2;
        }
        if (!Float.isFinite(vs = BRDConfig.verticalScale)) {
            vs = 0.5f;
        }
        if (vs < 0.05f) {
            vs = 0.05f;
        }
        if (vs > 2.0f) {
            vs = 2.0f;
        }
        int vsBits = Float.floatToIntBits(vs);
        double entityScale = (Double)mc.options.entityDistanceScaling().get();
        entityScale = Mth.clamp((double)entityScale, (double)0.01, (double)1.0);
        long esBits = Double.doubleToLongBits(entityScale);
        if (vd == CACHED_VD && vsBits == CACHED_VS_BITS && esBits == CACHED_ENTITY_SCALE_BITS) {
            return;
        }
        CACHED_VD = vd;
        CACHED_VS_BITS = vsBits;
        CACHED_ENTITY_SCALE_BITS = esBits;
        double radiusChunksH = (double)vd + 0.25;
        if (radiusChunksH < 2.0) {
            radiusChunksH = 2.0;
        }
        double radiusBlocksH = radiusChunksH * 16.0 * entityScale;
        R2_XZ_BLOCKS = radiusBlocksH * radiusBlocksH;
        INV_R2_XZ = 1.0 / R2_XZ_BLOCKS;
        double radiusChunksV = (double)vd * (double)vs + 0.5;
        if (radiusChunksV < 1.0) {
            radiusChunksV = 1.0;
        }
        double radiusBlocksV = radiusChunksV * 16.0 * entityScale;
        INV_R2_Y = 1.0 / (radiusBlocksV * radiusBlocksV);
    }

    @Inject(method={"shouldRender"}, at={@At(value="HEAD")}, cancellable=true)
    private void betterrenderdistance$cullEntitiesEllipsoid(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (!BRDConfig.enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return;
        }
        EntityRenderDispatcherCullMixin.updateCacheIfNeeded(mc);
        double dx = entity.getX() - camX;
        double dz = entity.getZ() - camZ;
        double d2xz = dx * dx + dz * dz;
        if (d2xz > R2_XZ_BLOCKS) {
            cir.setReturnValue(false);
            return;
        }
        AABB box = entity.getBoundingBox();
        double dy = EntityRenderDispatcherCullMixin.nearestToZero(box.minY - camY, box.maxY - camY);
        double norm = d2xz * INV_R2_XZ + dy * dy * INV_R2_Y;
        if (norm > 1.0) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static double nearestToZero(double a, double b) {
        if (a <= 0.0 && b >= 0.0) {
            return 0.0;
        }
        return a * a <= b * b ? a : b;
    }
}

