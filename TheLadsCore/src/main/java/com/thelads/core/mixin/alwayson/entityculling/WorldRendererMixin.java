/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.tr7zw.transition.mc.GeneralUtil
 *  net.minecraft.client.Camera
 *  net.minecraft.client.DeltaTracker
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.LevelRenderer
 *  net.minecraft.client.renderer.culling.Frustum
 *  net.minecraft.client.renderer.entity.EntityRenderDispatcher
 *  net.minecraft.client.renderer.entity.EntityRenderer
 *  net.minecraft.client.renderer.entity.state.AvatarRenderState
 *  net.minecraft.client.renderer.entity.state.EntityRenderState
 *  net.minecraft.client.renderer.state.level.LevelRenderState
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityAttachment
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.LivingEntity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.thelads.core.mixin.alwayson.entityculling;

import com.thelads.core.features.alwayson.entityculling.EntityCullingModBase;
import com.thelads.core.features.alwayson.entityculling.NMSCullingHelper;
import com.thelads.core.mixin.alwayson.entityculling.LivingEntityRendererAccessor;
import com.thelads.core.mixin.alwayson.entityculling.access.Cullable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={LevelRenderer.class})
public class WorldRendererMixin {
    private EntityRenderDispatcher entityCulling$entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
    private List<Runnable> lateRenders = new ArrayList<Runnable>();
    private double aabbExpansion = 0.5;

    @Inject(at={@At(value="HEAD")}, method={"extractEntity"}, cancellable=true)
    private void extractEntityRedir(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> ci) {
        if (EntityCullingModBase.instance.config.skipEntityCulling) {
            return;
        }
        Cullable cullable = (Cullable)entity;
        if (!cullable.isForcedVisible() && cullable.isCulled() && !NMSCullingHelper.ignoresCulling(entity)) {
            ++EntityCullingModBase.instance.skippedEntities;
            EntityRenderState state = new EntityRenderState();
            state.entityType = EntityType.INTERACTION;
            state = WorldRendererMixin.processNametag(entity, partialTick, state);
            state.x = Mth.lerp((double)partialTick, (double)entity.xOld, (double)entity.getX());
            state.y = Mth.lerp((double)partialTick, (double)entity.yOld, (double)entity.getY());
            state.z = Mth.lerp((double)partialTick, (double)entity.zOld, (double)entity.getZ());
            state.isInvisible = true;
            ci.setReturnValue(state);
            return;
        }
        ++EntityCullingModBase.instance.renderedEntities;
        cullable.setOutOfCamera(false);
    }

    private static EntityRenderState processNametag(Entity entity, float partialTick, EntityRenderState state) {
        if (EntityCullingModBase.instance.config.renderNametagsThroughWalls && entity.shouldShowName()) {
            if (entity instanceof LivingEntity) {
                LivingEntityRendererAccessor accessor;
                LivingEntity living = (LivingEntity)entity;
                EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer((Entity)living);
                double d = Minecraft.getInstance().getCameraEntity().distanceToSqr(entity);
                if (renderer instanceof LivingEntityRendererAccessor && (accessor = (LivingEntityRendererAccessor)renderer).invokeShouldShowName(living, d) && !entity.isDiscrete()) {
                    Component display;
                    Entity checkEntity = entity;
                    if (d < 100.0 && (display = checkEntity.belowNameDisplay()) != null) {
                        AvatarRenderState avatarState = new AvatarRenderState();
                        avatarState.entityType = EntityType.PLAYER;
                        avatarState.scoreText = display;
                        avatarState.isInvisibleToPlayer = true;
                        state = avatarState;
                    }
                    state.nameTag = entity.getDisplayName();
                    state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick));
                }
            } else {
                state.nameTag = entity.getDisplayName();
                state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick));
            }
        }
        return state;
    }

    @Inject(at={@At(value="HEAD")}, method={"extractVisibleEntities"})
    private void extractVisibleEntities(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState levelRenderState, CallbackInfo ci) {
        EntityCullingModBase.instance.frustum = frustum;
    }
}

