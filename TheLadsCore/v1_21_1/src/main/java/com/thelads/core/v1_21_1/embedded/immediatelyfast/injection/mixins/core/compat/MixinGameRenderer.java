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
package com.thelads.core.v1_21_1.embedded.immediatelyfast.injection.mixins.core.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.ImmediatelyFast;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.compat.CoreShaderBlacklist;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.core.ImmediatelyFastResourcePackMetadata;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Shadow
    @Final
    private Map<String, ShaderInstance> shaders;

    @Inject(method = "reloadShaders", at = @At("RETURN"))
    private void checkForCoreShaderModifications(ResourceProvider factory, CallbackInfo ci) {
        if (ImmediatelyFast.config.experimental_disable_resource_pack_conflict_handling) {
            return;
        }

        PackResources resourcePackWhichBreaksFontAtlasResizing = null;
        PackResources resourcePackWhichBreaksHudBatching = null;
        PackResources resourcePackWhichBreaksScreenBatching = null;
        try {
            final Set<PackResources> breakingResourcePacks = new HashSet<>();
            for (Map.Entry<String, ShaderInstance> shaderProgramEntry : this.shaders.entrySet()) {
                if (!CoreShaderBlacklist.isBlacklisted(shaderProgramEntry.getKey())) {
                    continue;
                }

                final ResourceLocation vertexShaderIdentifier = ResourceLocation.parse("shaders/core/" + shaderProgramEntry.getValue().getVertexProgram().getName() + ".vsh");
                final PackResources vertexShaderResourcePack = factory.getResource(vertexShaderIdentifier).map(Resource::source).orElse(null);
                if (vertexShaderResourcePack != null && !vertexShaderResourcePack.equals(Minecraft.getInstance().getVanillaPackResources())) {
                    breakingResourcePacks.add(vertexShaderResourcePack);
                }
                final ResourceLocation fragmentShaderIdentifier = ResourceLocation.parse("shaders/core/" + shaderProgramEntry.getValue().getFragmentProgram().getName() + ".fsh");
                final PackResources fragmentShaderResourcePack = factory.getResource(fragmentShaderIdentifier).map(Resource::source).orElse(null);
                if (fragmentShaderResourcePack != null && !fragmentShaderResourcePack.equals(Minecraft.getInstance().getVanillaPackResources())) {
                    breakingResourcePacks.add(fragmentShaderResourcePack);
                }
            }
            for (PackResources resourcePack : breakingResourcePacks) {
                ImmediatelyFastResourcePackMetadata metadata = resourcePack.getMetadataSection(ImmediatelyFastResourcePackMetadata.SERIALIZER);
                if (metadata == null) {
                    metadata = ImmediatelyFastResourcePackMetadata.DEFAULT;
                }
                if (!metadata.compatibleFeatures().contains("font_atlas_resizing")) {
                    resourcePackWhichBreaksFontAtlasResizing = resourcePack;
                }
                if (!metadata.compatibleFeatures().contains("hud_batching")) {
                    resourcePackWhichBreaksHudBatching = resourcePack;
                }
                if (!metadata.compatibleFeatures().contains("experimental_screen_batching")) {
                    resourcePackWhichBreaksScreenBatching = resourcePack;
                }
            }
        } catch (IOException e) {
            ImmediatelyFast.LOGGER.error("Failed to check for core shader modifications", e);
        }

        if (ImmediatelyFast.config.font_atlas_resizing) {
            if (resourcePackWhichBreaksFontAtlasResizing != null) {
                ImmediatelyFast.LOGGER.warn("Resource pack " + resourcePackWhichBreaksFontAtlasResizing.packId() + " is not compatible with font atlas resizing. Temporarily disabling font atlas resizing.");
                if (ImmediatelyFast.runtimeConfig.font_atlas_resizing) {
                    ImmediatelyFast.runtimeConfig.font_atlas_resizing = false;
                    this.immediatelyFast$reloadFontStorages();
                }
            } else if (!ImmediatelyFast.runtimeConfig.font_atlas_resizing) {
                ImmediatelyFast.LOGGER.info("Re-enabling font atlas resizing because no incompatible resource packs are loaded.");
                ImmediatelyFast.runtimeConfig.font_atlas_resizing = true;
                this.immediatelyFast$reloadFontStorages();
            }
        }
        if (ImmediatelyFast.config.hud_batching) {
            if (resourcePackWhichBreaksHudBatching != null) {
                ImmediatelyFast.LOGGER.warn("Resource pack " + resourcePackWhichBreaksHudBatching.packId() + " is not compatible with HUD batching. Temporarily disabling HUD batching.");
                ImmediatelyFast.runtimeConfig.hud_batching = false;
            } else if (!ImmediatelyFast.runtimeConfig.hud_batching) {
                ImmediatelyFast.LOGGER.info("Re-enabling HUD batching because no incompatible resource packs are loaded.");
                ImmediatelyFast.runtimeConfig.hud_batching = true;
            }
        }
        if (ImmediatelyFast.config.experimental_screen_batching) {
            if (resourcePackWhichBreaksScreenBatching != null) {
                ImmediatelyFast.LOGGER.warn("Resource pack " + resourcePackWhichBreaksScreenBatching.packId() + " is not compatible with experimental screen batching. Temporarily disabling experimental screen batching.");
                ImmediatelyFast.runtimeConfig.experimental_screen_batching = false;
            } else if (!ImmediatelyFast.runtimeConfig.experimental_screen_batching) {
                ImmediatelyFast.LOGGER.info("Re-enabling experimental screen batching because no incompatible resource packs are loaded.");
                ImmediatelyFast.runtimeConfig.experimental_screen_batching = true;
            }
        }
    }

    @Unique
    private void immediatelyFast$reloadFontStorages() {
        // Force reload the font storages to rebuild the font atlas textures
        Minecraft.getInstance().fontManager.updateOptions(Minecraft.getInstance().options);
    }

}
