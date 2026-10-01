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
package com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.sign_text_buffering;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.block.entity.SignText;

public class SignTextCache implements ResourceManagerReloadListener {

    public final SignAtlasFramebuffer signAtlasFramebuffer = new SignAtlasFramebuffer();
    public final Cache<SignText, SignAtlasFramebuffer.Slot> slotCache = CacheBuilder.newBuilder()
        .expireAfterAccess(5, TimeUnit.SECONDS)
        .removalListener(notification -> {
            if (notification.getCause().equals(RemovalCause.EXPLICIT)) {
                return;
            }

            final SignAtlasFramebuffer.Slot slot = (SignAtlasFramebuffer.Slot) notification.getValue();
            if (slot != null) {
                slot.markFree();
            }
        })
        .build();
    public final RenderType renderLayer = RenderType.text(this.signAtlasFramebuffer.getTextureId());

    public void clearCache() {
        this.slotCache.invalidateAll();
        this.signAtlasFramebuffer.clear();
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        this.clearCache();
    }

}
