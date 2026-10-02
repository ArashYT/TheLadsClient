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
// Added by The Lads: Xaero's Minimap ships this hook for ImmediatelyFast's own BatchableBufferSource (its radar icon
// tracing records the last render type per buffer source); the repackaged class needs the same hook to keep icons working.
package com.thelads.core.v1_21_11.embedded.immediatelyfast.injection.mixins.compat.xaerominimap;

import com.thelads.core.v1_21_11.embedded.immediatelyfast.feature.core.BatchableBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xaero.common.core.IBufferSource;
import xaero.common.core.XaeroMinimapCore;

@Mixin(value = BatchableBufferSource.class, remap = false)
public abstract class MixinXaero_BatchableBufferSource implements IBufferSource {

    @Unique
    private RenderType xaero_lastRenderType;

    @Override
    public RenderType getXaero_lastRenderType() {
        return this.xaero_lastRenderType;
    }

    @Override
    public void setXaero_lastRenderType(final RenderType lastRenderType) {
        this.xaero_lastRenderType = lastRenderType;
    }

    @ModifyVariable(method = "getBuffer", at = @At("HEAD"), argsOnly = true)
    private RenderType immediatelyFast$traceForXaero(final RenderType renderType) {
        XaeroMinimapCore.onBufferSourceGetBuffer(this, renderType);
        return renderType;
    }

}
