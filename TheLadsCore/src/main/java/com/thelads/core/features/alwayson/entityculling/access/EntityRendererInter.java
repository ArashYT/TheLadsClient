/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  net.minecraft.client.renderer.MultiBufferSource
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.phys.AABB
 */
package com.thelads.core.features.alwayson.entityculling.access;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public interface EntityRendererInter<T extends Entity> {
    public boolean shadowShouldShowName(T var1);

    public void shadowRenderNameTag(T var1, Component var2, PoseStack var3, MultiBufferSource var4, int var5, float var6);

    public boolean entityCullingIgnoresCulling(T var1);

    public AABB entityCullingGetCullingBox(T var1);
}

