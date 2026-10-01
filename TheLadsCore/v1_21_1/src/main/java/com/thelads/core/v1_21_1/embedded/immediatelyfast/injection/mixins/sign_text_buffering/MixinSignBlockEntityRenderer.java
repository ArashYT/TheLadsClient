/*
 * This file is part of ImmediatelyFast - https://github.com/RaphiMC/ImmediatelyFast
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
// Modified by The Lads: repackaged into Lads Core (com.thelads.core.v1_21_1.embedded.immediatelyfast).
package com.thelads.core.v1_21_1.embedded.immediatelyfast.injection.mixins.sign_text_buffering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.Vec3;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.ImmediatelyFast;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.core.BufferAllocatorPool;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.sign_text_buffering.NoSetTextAnglesMatrixStack;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.sign_text_buffering.SignAtlasFramebuffer;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.injection.interfaces.ISignText;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SignRenderer.class)
public abstract class MixinSignBlockEntityRenderer {

    @Shadow
    @Final
    private Font font;

    @Shadow
    abstract void renderSignText(BlockPos pos, SignText signText, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int lineHeight, int lineWidth, boolean front);

    @Shadow
    protected abstract void translateSignText(PoseStack matrices, boolean front, Vec3 translation);

    @Shadow
    abstract Vec3 getTextOffset();

    @Inject(method = "renderSignText", at = @At("HEAD"), cancellable = true)
    private void renderBufferedSignText(BlockPos pos, SignText signText, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int lineHeight, int lineWidth, boolean front, CallbackInfo ci) {
        if (matrices instanceof NoSetTextAnglesMatrixStack) {
            return;
        }
        final ISignText iSignText = (ISignText) signText;
        if (!iSignText.immediatelyFast$shouldCache()) {
            return;
        }

        SignAtlasFramebuffer.Slot slot = ImmediatelyFast.signTextCache.slotCache.getIfPresent(signText);
        if (slot == null) {
            final int width = this.immediatelyFast$getTextWidth(signText, lineWidth);
            final int height = 4 * lineHeight;
            if (width <= 0 || height <= 0) {
                iSignText.immediatelyFast$setShouldCache(false);
                return;
            }
            final int padding = signText.hasGlowingText() ? 2 : 0;

            slot = ImmediatelyFast.signTextCache.signAtlasFramebuffer.findSlot(width + padding, height + padding);
            if (slot != null) {
                final Matrix4f projectionMatrix = new Matrix4f().setOrtho(0F, SignAtlasFramebuffer.ATLAS_SIZE, SignAtlasFramebuffer.ATLAS_SIZE, 0F, -1000F, 1000F);
                RenderSystem.backupProjectionMatrix();
                RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.ORTHOGRAPHIC_Z);
                final Matrix4fStack modelViewMatrix = RenderSystem.getModelViewStack();
                modelViewMatrix.pushMatrix();
                modelViewMatrix.identity();
                RenderSystem.applyModelViewMatrix();
                final float fogStart = RenderSystem.getShaderFogStart();
                FogRenderer.setupNoFog();
                ImmediatelyFast.signTextCache.signAtlasFramebuffer.bindWrite(true);

                final ByteBufferBuilder bufferAllocator = BufferAllocatorPool.borrowBufferAllocator();
                final MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(bufferAllocator);
                final PoseStack matrixStack = new NoSetTextAnglesMatrixStack();
                matrixStack.translate(slot.x, slot.y, 0F);
                matrixStack.translate(slot.width / 2F, slot.height / 2F, 0F);
                this.renderSignText(Minecraft.getInstance().cameraEntity.blockPosition(), signText, matrixStack, immediate, light, lineHeight, lineWidth, front);
                immediate.endBatch();
                BufferAllocatorPool.returnBufferAllocatorSafe(bufferAllocator);

                Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
                RenderSystem.setShaderFogStart(fogStart);
                modelViewMatrix.popMatrix();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.restoreProjectionMatrix();

                ImmediatelyFast.signTextCache.slotCache.put(signText, slot);
            } else {
                ImmediatelyFast.LOGGER.warn("Failed to find a free slot for sign text (" + ImmediatelyFast.signTextCache.slotCache.size() + " sign texts in atlas). Falling back to immediate mode rendering.");
                iSignText.immediatelyFast$setShouldCache(false);
                return;
            }
        }

        float u1 = ((float) slot.x) / SignAtlasFramebuffer.ATLAS_SIZE;
        float u2 = ((float) slot.x + (float) slot.width) / SignAtlasFramebuffer.ATLAS_SIZE;
        float v1 = 1F - ((float) slot.y) / SignAtlasFramebuffer.ATLAS_SIZE;
        float v2 = 1F - ((float) slot.y + (float) slot.height) / SignAtlasFramebuffer.ATLAS_SIZE;

        if (signText.hasGlowingText()) {
            light = LightTexture.FULL_BRIGHT;
        }

        matrices.pushPose();
        this.translateSignText(matrices, front, this.getTextOffset());
        matrices.translate(-slot.width / 2F, -slot.height / 2F, 0F);
        final Matrix4f matrix4f = matrices.last().pose();
        final VertexConsumer vertexConsumer = vertexConsumers.getBuffer(ImmediatelyFast.signTextCache.renderLayer);
        vertexConsumer.addVertex(matrix4f, 0F, slot.height, 0F).setColor(255, 255, 255, 255).setUv(u1, v2).setLight(light);
        vertexConsumer.addVertex(matrix4f, slot.width, slot.height, 0F).setColor(255, 255, 255, 255).setUv(u2, v2).setLight(light);
        vertexConsumer.addVertex(matrix4f, slot.width, 0F, 0F).setColor(255, 255, 255, 255).setUv(u2, v1).setLight(light);
        vertexConsumer.addVertex(matrix4f, 0F, 0F, 0F).setColor(255, 255, 255, 255).setUv(u1, v1).setLight(light);
        matrices.popPose();

        ci.cancel();
    }

    @Redirect(method = "renderSignText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/SignRenderer;translateSignText(Lcom/mojang/blaze3d/vertex/PoseStack;ZLnet/minecraft/world/phys/Vec3;)V"))
    private void dontSetTextAngles(SignRenderer instance, PoseStack matrices, boolean front, Vec3 translation) {
        if (matrices instanceof NoSetTextAnglesMatrixStack) {
            return;
        }

        this.translateSignText(matrices, front, translation);
    }

    @Unique
    private int immediatelyFast$getTextWidth(final SignText signText, final int lineWidth) {
        final FormattedCharSequence[] orderedTexts = signText.getRenderMessages(Minecraft.getInstance().isTextFilteringEnabled(), text -> {
            final List<FormattedCharSequence> list = this.font.split(text, lineWidth);
            return list.isEmpty() ? FormattedCharSequence.EMPTY : list.get(0);
        });

        int width = 0;
        for (FormattedCharSequence orderedText : orderedTexts) {
            width = Math.max(width, this.font.width(orderedText));
        }
        if (width % 2 != 0) {
            width++; // Fixes issue which squishes the text when the width is odd (Test text: "hhhl")
        }

        return width;
    }

}
