/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.ObjectArrayList
 *  net.minecraft.client.Camera
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.LevelRenderer
 *  net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection
 *  net.minecraft.client.renderer.culling.Frustum
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.phys.Vec3
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.betterrenderdistance;

import com.thelads.core.features.alwayson.betterrenderdistance.config.BRDConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LevelRenderer.class})
public abstract class LevelRendererCullMixin {

    @Shadow
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
    @Shadow
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> nearbyVisibleSections;
    @Unique
    private static int brd$cachedVd = Integer.MIN_VALUE;
    @Unique
    private static int brd$cachedVsBits = 0;
    @Unique
    private static double brd$r2xz = 0.0;
    @Unique
    private static double brd$invR2xz = 0.0;
    @Unique
    private static double brd$invR2y = 0.0;

    @Inject(method={"cullTerrain"}, at={@At(value="TAIL")})
    private void brd$filterVisibleSections(Camera camera, Frustum frustum, boolean bl, CallbackInfo ci) {
        if (!BRDConfig.enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return;
        }
        LevelRendererCullMixin.brd$updateRadii(mc);
        Vec3 cam = camera.position();
        double camX = cam.x;
        double camY = cam.y;
        double camZ = cam.z;
        LevelRendererCullMixin.filterInPlaceEllipsoidCenter(this.visibleSections, camX, camY, camZ);
        LevelRendererCullMixin.filterInPlaceEllipsoidCenter(this.nearbyVisibleSections, camX, camY, camZ);
    }

    @Unique
    private static void brd$updateRadii(Minecraft mc) {
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
        if (vd == brd$cachedVd && vsBits == brd$cachedVsBits) {
            return;
        }
        brd$cachedVd = vd;
        brd$cachedVsBits = vsBits;
        double radiusChunksH = (double)vd + 0.25;
        if (radiusChunksH < 2.0) {
            radiusChunksH = 2.0;
        }
        double radiusBlocksH = radiusChunksH * 16.0;
        brd$r2xz = radiusBlocksH * radiusBlocksH;
        brd$invR2xz = 1.0 / brd$r2xz;
        double radiusChunksV = (double)vd * (double)vs + 0.5;
        if (radiusChunksV < 1.25) {
            radiusChunksV = 1.25;
        }
        double radiusBlocksV = radiusChunksV * 16.0;
        brd$invR2y = 1.0 / (radiusBlocksV * radiusBlocksV);
    }

    @Unique
    private static double brd$nearestToZero(double a, double b) {
        return a * a <= b * b ? a : b;
    }

    @Unique
    private static void filterInPlaceEllipsoidCenter(ObjectArrayList<SectionRenderDispatcher.RenderSection> list, double camX, double camY, double camZ) {
        int write = 0;
        int size = list.size();
        double r2xz = brd$r2xz;
        double invR2xz = brd$invR2xz;
        double invR2y = brd$invR2y;
        for (int i = 0; i < size; ++i) {
            double oy;
            double dy;
            double norm;
            double cz;
            double dz;
            SectionRenderDispatcher.RenderSection rs = (SectionRenderDispatcher.RenderSection)list.get(i);
            BlockPos origin = rs.getRenderOrigin();
            double cx = (double)origin.getX() + 8.0;
            double dx = cx - camX;
            double d2xz = dx * dx + (dz = (cz = (double)origin.getZ() + 8.0) - camZ) * dz;
            if (d2xz > r2xz || !((norm = d2xz * invR2xz + (dy = LevelRendererCullMixin.brd$nearestToZero((oy = (double)origin.getY() - camY) - 1.0, oy + 17.0)) * dy * invR2y) <= 1.0)) continue;
            list.set(write++, rs);
        }
        if (write != size) {
            list.removeElements(write, size);
        }
    }
}

