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
package com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.batching;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Stack;

public record BlendFuncDepthFuncState(boolean BLEND, boolean DEPTH_TEST, int GL_BLEND_SRC_RGB, int GL_BLEND_SRC_ALPHA, int GL_BLEND_DST_RGB, int GL_BLEND_DST_ALPHA, int GL_DEPTH_FUNC) {

    private static final Stack<BlendFuncDepthFuncState> STACK = new Stack<>();

    public static BlendFuncDepthFuncState current() {
        return new BlendFuncDepthFuncState(
            GlStateManager.BLEND.mode.enabled,
            GlStateManager.DEPTH.mode.enabled,
            GlStateManager.BLEND.srcRgb,
            GlStateManager.BLEND.srcAlpha,
            GlStateManager.BLEND.dstRgb,
            GlStateManager.BLEND.dstAlpha,
            GlStateManager.DEPTH.func
        );
    }

    public void saveAndApply() {
        STACK.push(current());
        this.apply();
    }

    public void revert() {
        STACK.pop().apply();
    }

    public void apply() {
        if (BLEND) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.blendFuncSeparate(GL_BLEND_SRC_RGB, GL_BLEND_DST_RGB, GL_BLEND_SRC_ALPHA, GL_BLEND_DST_ALPHA);

        if (DEPTH_TEST) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthFunc(GL_DEPTH_FUNC);
    }

}
