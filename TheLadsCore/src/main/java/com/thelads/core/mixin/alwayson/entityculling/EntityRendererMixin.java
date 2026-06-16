/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  net.minecraft.client.renderer.MultiBufferSource
 *  net.minecraft.client.renderer.entity.EntityRenderer
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.phys.AABB
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 */
package com.thelads.core.mixin.alwayson.entityculling;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.mixin.alwayson.entityculling.access.EntityRendererInter;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value={EntityRenderer.class})
public abstract class EntityRendererMixin<T extends Entity>
implements EntityRendererInter<T> {
    @Override
    public boolean shadowShouldShowName(T entity) {
        return ((EntityRenderer)(Object)this).createRenderState(entity, (float)0.0f).nameTag != null;
    }

    @Override
    public void shadowRenderNameTag(T entity, Component component, PoseStack poseStack, MultiBufferSource multiBufferSource, int light, float delta) {
    }

    @Override
    public boolean entityCullingIgnoresCulling(T entity) {
        return !this.affectedByCulling(entity);
    }

    @Override
    public AABB entityCullingGetCullingBox(T entity) {
        return this.getBoundingBoxForCulling(entity);
    }

    @Shadow
    abstract boolean affectedByCulling(T var1);

    @Shadow
    abstract AABB getBoundingBoxForCulling(T var1);
}

