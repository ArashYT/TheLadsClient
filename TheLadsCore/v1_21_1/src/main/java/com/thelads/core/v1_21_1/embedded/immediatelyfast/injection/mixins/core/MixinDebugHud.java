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
package com.thelads.core.v1_21_1.embedded.immediatelyfast.injection.mixins.core;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.ImmediatelyFast;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.core.BufferAllocatorPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = DebugScreenOverlay.class, priority = 9999)
public abstract class MixinDebugHud {

    @Inject(method = "getSystemInformation", at = @At("RETURN"))
    private void appendAllocationInfo(CallbackInfoReturnable<List<String>> cir) {
        if (ImmediatelyFast.config.dont_add_info_into_debug_hud) {
            return;
        }

        cir.getReturnValue().add("");
        cir.getReturnValue().add("ImmediatelyFast " + ImmediatelyFast.VERSION);
        cir.getReturnValue().add("Buffer Pool: " + BufferAllocatorPool.getSize());
    }

}
