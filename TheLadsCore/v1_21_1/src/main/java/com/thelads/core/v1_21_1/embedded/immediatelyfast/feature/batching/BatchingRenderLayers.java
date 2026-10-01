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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.BiFunction;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;

import static net.minecraft.Util.memoize;

/**
 * Class which defines how different elements (RenderLayer's) are rendered.
 */
public class BatchingRenderLayers {

    public static final BiFunction<Integer, BlendFuncDepthFuncState, RenderType> TEXTURE = memoize((id, blendFuncDepthFunc) -> new ImmediatelyFastRenderLayer("texture", VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX, false, () -> {
        blendFuncDepthFunc.saveAndApply();
        RenderSystem.setShaderTexture(0, id);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
    }, blendFuncDepthFunc::revert));

    public static final BiFunction<Integer, BlendFuncDepthFuncState, RenderType> COLORED_TEXTURE = memoize((id, blendFuncDepthFunc) -> new ImmediatelyFastRenderLayer("colored_texture", VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR, false, () -> {
        blendFuncDepthFunc.saveAndApply();
        RenderSystem.setShaderTexture(0, id);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }, blendFuncDepthFunc::revert));

    private static class ImmediatelyFastRenderLayer extends RenderType {

        private ImmediatelyFastRenderLayer(final String name, final VertexFormat.Mode drawMode, final VertexFormat vertexFormat, final boolean translucent, final Runnable startAction, final Runnable endAction) {
            super("immediatelyfast_" + name, vertexFormat, drawMode, 2048, false, translucent, startAction, endAction);
        }

    }

}
