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
// Modified by The Lads: repackaged into Lads Core (com.thelads.core.v1_20_1.embedded.immediatelyfast).
package com.thelads.core.v1_20_1.embedded.immediatelyfast.forge.injection.mixins.screen_batching;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.thelads.core.v1_20_1.embedded.immediatelyfast.feature.batching.BatchingBuffers;
import com.thelads.core.v1_20_1.embedded.immediatelyfast.injection.processors.InjectAboveEverything;
import com.thelads.core.v1_20_1.embedded.immediatelyfast.injection.processors.InjectOnAllReturns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractContainerScreen.class, priority = 1500)
public abstract class MixinHandledScreen {

    @InjectAboveEverything
    @Inject(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", at = @At("HEAD"), remap = false)
    private static void beginDrawSlotHighlightOnTop(CallbackInfo ci) {
        BatchingBuffers.beginItemOverlayRendering();
    }

    @InjectOnAllReturns
    @Inject(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", at = @At("RETURN"), remap = false)
    private static void endDrawSlotHighlightOnTop(CallbackInfo ci) {
        BatchingBuffers.endItemOverlayRendering();
    }

}
