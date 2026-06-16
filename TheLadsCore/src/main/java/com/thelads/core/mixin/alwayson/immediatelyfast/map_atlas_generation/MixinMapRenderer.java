/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.sugar.Local
 *  net.minecraft.client.renderer.MapRenderer
 *  net.minecraft.client.renderer.SubmitNodeCollector$CustomGeometryRenderer
 *  net.minecraft.client.renderer.state.MapRenderState
 *  net.minecraft.client.resources.MapTextureManager
 *  net.minecraft.world.level.saveddata.maps.MapId
 *  net.minecraft.world.level.saveddata.maps.MapItemSavedData
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.ModifyArg
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.map_atlas_generation;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast;
import com.thelads.core.features.alwayson.immediatelyfast.feature.map_atlas_generation.MapAtlasTexture;
import com.thelads.core.mixin.alwayson.immediatelyfast.interfaces.IMapRenderState;
import com.thelads.core.mixin.alwayson.immediatelyfast.interfaces.IMapTextureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={MapRenderer.class})
public abstract class MixinMapRenderer {
    @Shadow
    @Final
    private MapTextureManager mapTextureManager;

    @ModifyArg(method={"render"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V", ordinal=0))
    private SubmitNodeCollector.CustomGeometryRenderer modifyTextureCoordinates(SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer, @Local(argsOnly=true) MapRenderState renderState, @Local(argsOnly=true) int light) {
        IMapRenderState immediatelyFast$renderState = (IMapRenderState)renderState;
        if (immediatelyFast$renderState.immediatelyFast$getAtlasTexture() != null && immediatelyFast$renderState.immediatelyFast$getAtlasTexture().getTextureId().equals(renderState.texture)) {
            float u1 = (float)immediatelyFast$renderState.immediatelyFast$getAtlasX() / (float)MapAtlasTexture.ATLAS_SIZE;
            float u2 = (float)(immediatelyFast$renderState.immediatelyFast$getAtlasX() + 128) / (float)MapAtlasTexture.ATLAS_SIZE;
            float v1 = (float)immediatelyFast$renderState.immediatelyFast$getAtlasY() / (float)MapAtlasTexture.ATLAS_SIZE;
            float v2 = (float)(immediatelyFast$renderState.immediatelyFast$getAtlasY() + 128) / (float)MapAtlasTexture.ATLAS_SIZE;
            return (matrix, vertexConsumer) -> {
                vertexConsumer.addVertex(matrix, 0.0f, 128.0f, -0.01f).setColor(-1).setUv(u1, v2).setLight(light);
                vertexConsumer.addVertex(matrix, 128.0f, 128.0f, -0.01f).setColor(-1).setUv(u2, v2).setLight(light);
                vertexConsumer.addVertex(matrix, 128.0f, 0.0f, -0.01f).setColor(-1).setUv(u2, v1).setLight(light);
                vertexConsumer.addVertex(matrix, 0.0f, 0.0f, -0.01f).setColor(-1).setUv(u1, v1).setLight(light);
            };
        }
        return customGeometryRenderer;
    }

    @Inject(method={"extractRenderState"}, at={@At(value="RETURN")})
    private void initAtlasParameters(MapId mapId, MapItemSavedData mapState, MapRenderState renderState, CallbackInfo ci) {
        int packedLocation = ((IMapTextureManager)this.mapTextureManager).immediatelyFast$getAtlasMapping(mapId.id());
        if (packedLocation == -1) {
            ImmediatelyFast.LOGGER.warn("Map " + mapId.id() + " is not in an atlas");
            return;
        }
        IMapRenderState immediatelyFast$renderState = (IMapRenderState)renderState;
        immediatelyFast$renderState.immediatelyFast$setAtlasX((packedLocation >> 8 & 0xFF) * 128);
        immediatelyFast$renderState.immediatelyFast$setAtlasY((packedLocation & 0xFF) * 128);
        immediatelyFast$renderState.immediatelyFast$setAtlasTexture(((IMapTextureManager)this.mapTextureManager).immediatelyFast$getMapAtlasTexture(packedLocation >> 16));
        if (immediatelyFast$renderState.immediatelyFast$getAtlasTexture() == null) {
            throw new IllegalStateException("getMapAtlasTexture returned null for packedLocation " + packedLocation + " (map " + mapId.id() + ")");
        }
    }
}

