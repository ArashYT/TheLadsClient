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
package com.thelads.core.v1_20_1.embedded.immediatelyfast.forge.injection.mixins.hud_batching;

import com.thelads.core.v1_20_1.embedded.immediatelyfast.ImmediatelyFast;
import com.thelads.core.v1_20_1.embedded.immediatelyfast.feature.batching.BatchingBuffers;
import com.thelads.core.v1_20_1.embedded.immediatelyfast.injection.processors.InjectAboveEverything;
import com.thelads.core.v1_20_1.embedded.immediatelyfast.injection.processors.InjectOnAllReturns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets = "net.minecraftforge.client.gui.overlay.ForgeGui", priority = 1500, remap = false) // The Lads: by name, Core builds without Forge
public abstract class MixinForgeGui {

    @InjectAboveEverything
    @Inject(method = {
            "renderArmor",
            "renderAir",
            "renderFood",
            "renderTitle",
            "renderHealthMount"
    }, at = @At("HEAD"))
    private void beginBatching(CallbackInfo ci) {
        if (ImmediatelyFast.runtimeConfig.hud_batching) {
            BatchingBuffers.beginHudBatching();
        }
    }

    @InjectOnAllReturns
    @Inject(method = {
            "renderArmor",
            "renderAir",
            "renderFood",
            "renderTitle",
            "renderHealthMount"
    }, at = @At("RETURN"))
    private void endBatching(CallbackInfo ci) {
        if (ImmediatelyFast.runtimeConfig.hud_batching) {
            BatchingBuffers.endHudBatching();
        }
    }

}
