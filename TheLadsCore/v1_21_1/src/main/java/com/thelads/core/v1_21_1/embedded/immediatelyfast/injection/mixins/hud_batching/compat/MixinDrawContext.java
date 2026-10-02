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
package com.thelads.core.v1_21_1.embedded.immediatelyfast.injection.mixins.hud_batching.compat;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.batching.HudBatchingBufferSource;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.core.BatchableBufferSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class MixinDrawContext {

    @Shadow
    public abstract void flush();

    @Shadow
    public MultiBufferSource.BufferSource bufferSource;

    @Shadow
    @Final
    private PoseStack pose;

    @Shadow
    protected abstract void flushIfManaged();

    @WrapMethod(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V")
    private void renderItemDecorations(final Font textRenderer, final ItemStack stack, final int x, final int y, final String countOverride, final Operation<Void> original) {
        if (this.bufferSource instanceof HudBatchingBufferSource hudBatchingBufferSource) {
            hudBatchingBufferSource.setRenderingItemDecorations(true);
        }
        try {
            original.call(textRenderer, stack, x, y, countOverride);
        } finally {
            if (this.bufferSource instanceof HudBatchingBufferSource hudBatchingBufferSource) {
                hudBatchingBufferSource.setRenderingItemDecorations(false);
            }
        }
    }

    @WrapMethod(method = "flush()V")
    private void restoreDepthTestState(final Operation<Void> original) {
        final boolean currentDepthTestState = GlStateManager.DEPTH.mode.enabled;
        original.call();
        if (this.bufferSource instanceof BatchableBufferSource && GlStateManager.DEPTH.mode.enabled != currentDepthTestState) {
            if (currentDepthTestState) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
        }
    }

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemCooldowns;getCooldownPercent(Lnet/minecraft/world/item/Item;F)F")), at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"))
    private void forceDraw(CallbackInfo ci) {
        if (this.bufferSource instanceof BatchableBufferSource) {
            this.flush();
        }
    }

    @Redirect(method = "applyScissor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flushIfManaged()V"))
    private void drawIfBatching(GuiGraphics instance) {
        if (this.bufferSource instanceof BatchableBufferSource) {
            this.flush();
        } else {
            this.flushIfManaged();
        }
    }

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void translateZForAllItemOverlays(CallbackInfo ci) {
        if (this.bufferSource instanceof BatchableBufferSource) {
            this.pose.translate(0F, 0F, 200F);
        }
    }

    @WrapWithCondition(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private boolean translateZEarlier(PoseStack instance, float x, float y, float z) {
        return !(this.bufferSource instanceof BatchableBufferSource);
    }

}
