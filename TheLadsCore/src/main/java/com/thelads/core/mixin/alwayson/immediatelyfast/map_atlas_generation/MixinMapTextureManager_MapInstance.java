/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.sugar.Local
 *  com.mojang.blaze3d.platform.NativeImage
 *  com.mojang.blaze3d.systems.RenderSystem
 *  net.lenni0451.reflect.Objects
 *  net.minecraft.client.renderer.texture.AbstractTexture
 *  net.minecraft.client.renderer.texture.DynamicTexture
 *  net.minecraft.client.renderer.texture.TextureManager
 *  net.minecraft.client.resources.MapTextureManager
 *  net.minecraft.client.resources.MapTextureManager$MapInstance
 *  net.minecraft.world.level.material.MapColor
 *  net.minecraft.world.level.saveddata.maps.MapItemSavedData
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.map_atlas_generation;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast;
import com.thelads.core.features.alwayson.immediatelyfast.feature.map_atlas_generation.MapAtlasTexture;
import com.thelads.core.features.alwayson.immediatelyfast.injection.interfaces.IMapTextureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={MapTextureManager.MapInstance.class})
public abstract class MixinMapTextureManager_MapInstance {
    @Shadow
    private MapItemSavedData data;
    @Mutable
    @Shadow
    @Final
    private DynamicTexture texture;
    @Shadow
    private boolean requiresUpload;
    @Shadow
    @Final
    @Mutable
    Identifier location;
    @Unique
    private static final DynamicTexture DUMMY_TEXTURE;
    static {
        DynamicTexture dummy = null;
        try {
            java.lang.reflect.Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
            dummy = (DynamicTexture) unsafe.allocateInstance(DynamicTexture.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        DUMMY_TEXTURE = dummy;
    }
    @Unique
    private int immediatelyFast$atlasX;
    @Unique
    private int immediatelyFast$atlasY;
    @Unique
    private MapAtlasTexture immediatelyFast$atlasTexture;

    @Redirect(method={"<init>"}, at=@At(value="NEW", target="(Ljava/util/function/Supplier;IIZ)Lnet/minecraft/client/renderer/texture/DynamicTexture;"))
    private DynamicTexture initAtlasParametersAndDontAllocateTexture(Supplier<String> label, int width, int height, boolean useCalloc, @Local(argsOnly=true) MapTextureManager mapTextureManager, @Local(argsOnly=true) int id) {
        int packedLocation = ((IMapTextureManager)mapTextureManager).immediatelyFast$getAtlasMapping(id);
        if (packedLocation == -1) {
            ImmediatelyFast.LOGGER.warn("Map " + id + " is not in an atlas");
            return new DynamicTexture(label, width, height, useCalloc);
        }
        this.immediatelyFast$atlasX = (packedLocation >> 8 & 0xFF) * 128;
        this.immediatelyFast$atlasY = (packedLocation & 0xFF) * 128;
        this.immediatelyFast$atlasTexture = ((IMapTextureManager)mapTextureManager).immediatelyFast$getMapAtlasTexture(packedLocation >> 16);
        if (this.immediatelyFast$atlasTexture == null) {
            throw new IllegalStateException("getMapAtlasTexture returned null for packedLocation " + packedLocation + " (map " + id + ")");
        }
        return DUMMY_TEXTURE;
    }

    @Redirect(method={"<init>"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/texture/TextureManager;register(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V"))
    private void getAtlasTextureIdentifier(TextureManager instance, Identifier path, AbstractTexture texture) {
        if (this.immediatelyFast$atlasTexture != null) {
            this.texture = null;
            this.location = this.immediatelyFast$atlasTexture.getTextureId();
        } else {
            instance.register(path, texture);
        }
    }

    @Inject(method={"updateTextureIfNeeded"}, at={@At(value="HEAD")}, cancellable=true)
    private void updateAtlasTexture(CallbackInfo ci) {
        if (this.requiresUpload && this.immediatelyFast$atlasTexture != null) {
            ci.cancel();
            DynamicTexture atlasTexture = this.immediatelyFast$atlasTexture.getTexture();
            NativeImage atlasImage = atlasTexture.getPixels();
            if (atlasImage == null) {
                throw new IllegalStateException("Atlas texture has already been closed");
            }
            for (int x = 0; x < 128; ++x) {
                for (int y = 0; y < 128; ++y) {
                    int i = x + y * 128;
                    atlasImage.setPixel(this.immediatelyFast$atlasX + x, this.immediatelyFast$atlasY + y, MapColor.getColorFromPackedId((int)this.data.colors[i]));
                }
            }
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(atlasTexture.getTexture(), atlasImage, 0, 0, this.immediatelyFast$atlasX, this.immediatelyFast$atlasY);
            this.requiresUpload = false;
        }
    }

    @Inject(method={"close"}, at={@At(value="HEAD")}, cancellable=true)
    private void dontCloseDummyTexture(CallbackInfo ci) {
        if (this.immediatelyFast$atlasTexture != null) {
            ci.cancel();
        }
    }
}

