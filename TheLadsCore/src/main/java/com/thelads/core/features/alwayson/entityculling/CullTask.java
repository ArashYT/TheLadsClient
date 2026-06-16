/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  net.minecraft.client.Minecraft
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Position
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 */
package com.thelads.core.features.alwayson.entityculling;

import com.thelads.core.features.alwayson.entityculling.occlusionculling.OcclusionCullingInstance;
import com.thelads.core.features.alwayson.entityculling.occlusionculling.util.Vec3d;
import com.thelads.core.features.alwayson.entityculling.EntityCullingModBase;
import com.thelads.core.features.alwayson.entityculling.NMSCullingHelper;
import com.thelads.core.features.alwayson.entityculling.versionless.access.Cullable;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CullTask
implements Runnable {
    public boolean requestCull = false;
    public boolean disableEntityCulling = false;
    public boolean disableBlockEntityCulling = false;
    private final OcclusionCullingInstance culling;
    private final Minecraft client = Minecraft.getInstance();
    private final int sleepDelay;
    private final int hitboxLimit;
    private final Set<BlockEntityType<?>> blockEntityWhitelist;
    private final Set<EntityType<?>> entityWhistelist;
    public double lastTime;
    private Vec3d lastPos;
    private Vec3d aabbMin;
    private Vec3d aabbMax;
    private boolean ingame;
    private List<Entity> entitiesForRendering;
    private Map<BlockPos, BlockEntity> blockEntities;
    private Vec3 cameraMC;

    public CullTask(OcclusionCullingInstance culling, Set<BlockEntityType<?>> blockEntityWhitelist, Set<EntityType<?>> entityWhistelist) {
        this.sleepDelay = EntityCullingModBase.instance.config.sleepDelay;
        this.hitboxLimit = EntityCullingModBase.instance.config.hitboxLimit;
        this.lastTime = 0.0;
        this.lastPos = new Vec3d(0.0, 0.0, 0.0);
        this.aabbMin = new Vec3d(0.0, 0.0, 0.0);
        this.aabbMax = new Vec3d(0.0, 0.0, 0.0);
        this.ingame = false;
        this.entitiesForRendering = new ArrayList<Entity>();
        this.blockEntities = new HashMap<BlockPos, BlockEntity>();
        this.cameraMC = new Vec3(0.0, 0.0, 0.0);
        this.culling = culling;
        this.blockEntityWhitelist = blockEntityWhitelist;
        this.entityWhistelist = entityWhistelist;
    }

    @Override
    public void run() {
        while (Minecraft.getInstance().isRunning()) {
            try {
                Thread.sleep(this.sleepDelay);
                if (EntityCullingModBase.enabled && this.ingame) {
                    if (!this.requestCull && this.cameraMC.x == this.lastPos.x && this.cameraMC.y == this.lastPos.y && this.cameraMC.z == this.lastPos.z) continue;
                    long start = System.nanoTime();
                    this.requestCull = false;
                    this.lastPos.set(this.cameraMC.x, this.cameraMC.y, this.cameraMC.z);
                    Vec3d camera = this.lastPos;
                    this.culling.resetCache();
                    this.cullBlockEntities(this.cameraMC, camera);
                    this.cullEntities(this.cameraMC, camera);
                    this.lastTime = (double)(System.nanoTime() - start) / 1000000.0;
                    continue;
                }
                this.lastTime = 0.0;
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("Shutting down culling task!");
    }

    private void cullEntities(Vec3 cameraMC, Vec3d camera) {
        if (this.disableEntityCulling) {
            return;
        }
        for (Entity entity2 : this.entitiesForRendering) {
            Cullable cullable;
            if (entity2 == null) break;
            if (!(entity2 instanceof Cullable) || this.entityWhistelist.contains(entity2.getType()) || EntityCullingModBase.instance.isDynamicWhitelisted(entity2) || (cullable = (Cullable)entity2).isForcedVisible()) continue;
            if (Minecraft.getInstance().shouldEntityAppearGlowing(entity2)) {
                cullable.setCulled(false);
                continue;
            }
            if (!entity2.position().closerThan((Position)cameraMC, (double)EntityCullingModBase.instance.config.tracingDistance)) {
                cullable.setCulled(false);
                continue;
            }
            AABB boundingBox = NMSCullingHelper.getCullingBox(entity2);
            if (boundingBox == null || boundingBox.getXsize() > (double)this.hitboxLimit || boundingBox.getYsize() > (double)this.hitboxLimit || boundingBox.getZsize() > (double)this.hitboxLimit) {
                cullable.setCulled(false);
                continue;
            }
            this.aabbMin.set(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
            this.aabbMax.set(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
            boolean visible = this.culling.isAABBVisible(this.aabbMin, this.aabbMax, camera);
            cullable.setCulled(!visible);
        }
    }

    private void cullBlockEntities(Vec3 cameraMC, Vec3d camera) {
        if (this.disableBlockEntityCulling) {
            return;
        }
        Iterator<Map.Entry<BlockPos, BlockEntity>> iterator = this.blockEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            BlockPos pos;
            Cullable cullable;
            Map.Entry<BlockPos, BlockEntity> entry;
            try {
                entry = iterator.next();
            }
            catch (NullPointerException | ConcurrentModificationException ex) {
                break;
            }
            if (entry == null) break;
            if (this.blockEntityWhitelist.contains(entry.getValue().getType()) || this.client.getBlockEntityRenderDispatcher().getRenderer(entry.getValue()) == null || EntityCullingModBase.instance.isDynamicWhitelisted(entry.getValue()) || (cullable = (Cullable)entry.getValue()).isForcedVisible() || !CullTask.closerThan(pos = entry.getKey(), (Position)cameraMC, 64.0)) continue;
            AABB boundingBox = EntityCullingModBase.instance.setupAABB(entry.getValue(), pos);
            if (boundingBox.getXsize() > (double)this.hitboxLimit || boundingBox.getYsize() > (double)this.hitboxLimit || boundingBox.getZsize() > (double)this.hitboxLimit) {
                cullable.setCulled(false);
                continue;
            }
            this.aabbMin.set(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
            this.aabbMax.set(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
            boolean visible = this.culling.isAABBVisible(this.aabbMin, this.aabbMax, camera);
            cullable.setCulled(!visible);
        }
    }

    private static boolean closerThan(BlockPos blockPos, Position position, double d) {
        return CullTask.distSqr(blockPos, position.x(), position.y(), position.z(), true) < d * d;
    }

    private static double distSqr(BlockPos blockPos, double d, double e, double f, boolean bl) {
        double g = bl ? 0.5 : 0.0;
        double h = (double)blockPos.getX() + g - d;
        double i = (double)blockPos.getY() + g - e;
        double j = (double)blockPos.getZ() + g - f;
        return h * h + i * i + j * j;
    }

    public void setIngame(boolean ingame) {
        this.ingame = ingame;
    }

    public void setEntitiesForRendering(List<Entity> entitiesForRendering) {
        this.entitiesForRendering = entitiesForRendering;
    }

    public void setBlockEntities(Map<BlockPos, BlockEntity> blockEntities) {
        this.blockEntities = blockEntities;
    }

    public void setCameraMC(Vec3 cameraMC) {
        this.cameraMC = cameraMC;
    }
}

