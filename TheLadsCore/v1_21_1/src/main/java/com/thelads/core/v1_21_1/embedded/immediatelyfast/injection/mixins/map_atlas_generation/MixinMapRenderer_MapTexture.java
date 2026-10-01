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
package com.thelads.core.v1_21_1.embedded.immediatelyfast.injection.mixins.map_atlas_generation;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.lenni0451.reflect.Objects;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.ImmediatelyFast;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.map_atlas_generation.MapAtlasTexture;
import com.thelads.core.v1_21_1.embedded.immediatelyfast.injection.interfaces.IMapRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.map_atlas_generation.MapAtlasTexture.ATLAS_SIZE;
import static com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.map_atlas_generation.MapAtlasTexture.MAP_SIZE;

@Mixin(value = MapRenderer.MapInstance.class, priority = 1100) // Workaround for Porting-Lib which relies on the LVT to be intact
public abstract class MixinMapRenderer_MapTexture {

    @Shadow
    private MapItemSavedData data;

    @Mutable
    @Shadow
    @Final
    private DynamicTexture texture;

    @Shadow
    @Final
    MapRenderer field_2047;

    @Unique
    private static final DynamicTexture DUMMY_TEXTURE = Objects.allocate(DynamicTexture.class);

    @Unique
    private int immediatelyFast$atlasX;

    @Unique
    private int immediatelyFast$atlasY;

    @Unique
    private MapAtlasTexture immediatelyFast$atlasTexture;

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "(IIZ)Lnet/minecraft/client/renderer/texture/DynamicTexture;"))
    private DynamicTexture initAtlasParametersAndDontAllocateTexture(int width, int height, boolean useMipmaps, @Local int id) {
        final int packedLocation = ((IMapRenderer) this.field_2047).immediatelyFast$getAtlasMapping(id);
        if (packedLocation == -1) {
            ImmediatelyFast.LOGGER.warn("Map " + id + " is not in an atlas");
            // Leave atlasTexture null to indicate that this map is not in an atlas, and it should use the vanilla system instead
            return new DynamicTexture(width, height, useMipmaps);
        }

        this.immediatelyFast$atlasX = ((packedLocation >> 8) & 0xFF) * MAP_SIZE;
        this.immediatelyFast$atlasY = (packedLocation & 0xFF) * MAP_SIZE;
        this.immediatelyFast$atlasTexture = ((IMapRenderer) this.field_2047).immediatelyFast$getMapAtlasTexture(packedLocation >> 16);
        if (this.immediatelyFast$atlasTexture == null) {
            throw new IllegalStateException("getMapAtlasTexture returned null for packedLocation " + packedLocation + " (map " + id + ")");
        }

        return DUMMY_TEXTURE;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureManager;register(Ljava/lang/String;Lnet/minecraft/client/renderer/texture/DynamicTexture;)Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation getAtlasTextureIdentifier(TextureManager textureManager, String id, DynamicTexture texture) {
        if (this.immediatelyFast$atlasTexture != null) {
            this.texture = null; // Don't leave the texture field pointing to the uninitialized dummy texture
            return this.immediatelyFast$atlasTexture.getIdentifier();
        } else {
            return textureManager.register(id, texture);
        }
    }

    @Inject(method = "updateTexture", at = @At("HEAD"), cancellable = true)
    private void updateAtlasTexture(CallbackInfo ci) {
        if (this.immediatelyFast$atlasTexture != null) {
            ci.cancel();
            final DynamicTexture atlasTexture = this.immediatelyFast$atlasTexture.getTexture();
            final NativeImage atlasImage = atlasTexture.getPixels();
            if (atlasImage == null) {
                throw new IllegalStateException("Atlas texture has already been closed");
            }

            for (int x = 0; x < MAP_SIZE; x++) {
                for (int y = 0; y < MAP_SIZE; y++) {
                    final int i = x + y * MAP_SIZE;
                    atlasImage.setPixelRGBA(this.immediatelyFast$atlasX + x, this.immediatelyFast$atlasY + y, MapColor.getColorFromPackedId(this.data.colors[i]));
                }
            }
            atlasTexture.bind();
            atlasImage.upload(0, this.immediatelyFast$atlasX, this.immediatelyFast$atlasY, this.immediatelyFast$atlasX, this.immediatelyFast$atlasY, MAP_SIZE, MAP_SIZE, false, false);
        }
    }

    @Redirect(method = "draw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setUv(FF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"), slice = @Slice(from = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;addVertex(Lorg/joml/Matrix4f;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", ordinal = 0), to = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setLight(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;", ordinal = 3)))
    private VertexConsumer drawAtlasTexture(VertexConsumer instance, float u, float v) {
        if (this.immediatelyFast$atlasTexture != null) {
            if (u == 0 && v == 1) {
                u = (float) this.immediatelyFast$atlasX / ATLAS_SIZE;
                v = (float) (this.immediatelyFast$atlasY + MAP_SIZE) / ATLAS_SIZE;
            } else if (u == 1 && v == 1) {
                u = (float) (this.immediatelyFast$atlasX + MAP_SIZE) / ATLAS_SIZE;
                v = (float) (this.immediatelyFast$atlasY + MAP_SIZE) / ATLAS_SIZE;
            } else if (u == 1 && v == 0) {
                u = (float) (this.immediatelyFast$atlasX + MAP_SIZE) / ATLAS_SIZE;
                v = (float) this.immediatelyFast$atlasY / ATLAS_SIZE;
            } else if (u == 0 && v == 0) {
                u = (float) this.immediatelyFast$atlasX / ATLAS_SIZE;
                v = (float) this.immediatelyFast$atlasY / ATLAS_SIZE;
            }
        }

        return instance.setUv(u, v);
    }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void dontCloseDummyTexture(CallbackInfo ci) {
        if (this.immediatelyFast$atlasTexture != null) {
            ci.cancel();
        }
    }

}
