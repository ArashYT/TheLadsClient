package com.thelads.core.mixin.alwayson.skinlayers;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.features.alwayson.skinlayers.SkinLayersModBase;
import com.thelads.core.features.alwayson.skinlayers.SkinUtil;
import com.thelads.core.features.alwayson.skinlayers.SkullRendererCache;
import com.thelads.core.features.alwayson.skinlayers.accessor.SkullModelStateAccessor;
import com.thelads.core.features.alwayson.skinlayers.accessor.SkullSettings;
import com.thelads.core.features.alwayson.skinlayers.accessor.SkullBlockRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkullBlockRenderer.class)
public class SkullBlockEntityRendererMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void extractRenderState(SkullBlockEntity skullBlockEntity, SkullBlockRenderState skullBlockRenderState, float f, Vec3 vec3, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, CallbackInfo ci) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("SkinLayers").isEnabled()) {
            SkullRendererCache.renderNext = false;
            SkullRendererCache.lastSkull = null;
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        if (!SkinLayersModBase.config.enableSkulls) {
            return;
        }
        if (this.internalDistToCenterSqr(skullBlockEntity.getBlockPos(), camera.x(), camera.y(), camera.z()) < (double) (SkinLayersModBase.config.renderDistanceLOD * SkinLayersModBase.config.renderDistanceLOD)) {
            SkullRendererCache.lastSkull = (SkullSettings) skullBlockEntity;
            ResolvableProfile ownerProfile = skullBlockEntity.getOwnerProfile();
            GameProfile gameProfile = ownerProfile != null ? ownerProfile.partialProfile() : null;
            if (gameProfile == null) {
                return;
            }
            PlayerSkin playerSkin = Minecraft.getInstance().getSkinManager().createLookup(gameProfile, false).get();
            Identifier textureLocation = playerSkin != null && playerSkin.body() != null ? playerSkin.body().texturePath() : null;
            Identifier lastTexture = SkullRendererCache.lastSkull.getLastTexture();
            if (textureLocation == null || !textureLocation.equals(lastTexture)) {
                SkullRendererCache.lastSkull.setInitialized(false);
                SkullRendererCache.lastSkull.setLastTexture(textureLocation);
            }
            if (!SkullRendererCache.lastSkull.initialized() && SkullRendererCache.lastSkull.getHeadLayers() == null) {
                if (textureLocation != null) {
                    SkullRendererCache.lastSkull.setLastTexture(textureLocation);
                    SkinUtil.setup3dLayers(gameProfile, SkullRendererCache.lastSkull);
                }
            }
            SkullRendererCache.renderNext = SkullRendererCache.lastSkull.getHeadLayers() != null;
            if (SkullRendererCache.renderNext && skullBlockRenderState instanceof SkullBlockRenderStateAccessor) {
                ((SkullBlockRenderStateAccessor) skullBlockRenderState).skinlayers$setSkullSettings(SkullRendererCache.lastSkull);
            }
        } else {
            SkullRendererCache.renderNext = false;
            SkullRendererCache.lastSkull = null;
        }
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void submit(SkullBlockRenderState skullBlockRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (SkullRendererCache.renderNext && skullBlockRenderState instanceof SkullBlockRenderStateAccessor) {
            SkullRendererCache.lastSkull = ((SkullBlockRenderStateAccessor) skullBlockRenderState).skinlayers$getSkullSettings();
        } else {
            SkullRendererCache.lastSkull = null;
        }
    }

    @ModifyVariable(method = "submitSkull", at = @At("STORE"), ordinal = 0)
    private static SkullModelBase.State submitSkull(SkullModelBase.State state) {
        if (state instanceof SkullModelStateAccessor) {
            SkullModelStateAccessor accessor = (SkullModelStateAccessor) state;
            accessor.setSkullSettings(SkullRendererCache.lastSkull);
            SkullRendererCache.lastSkull = null;
        }
        return state;
    }

    private double internalDistToCenterSqr(BlockPos pos, double d, double e, double f) {
        double g = (double) pos.getX() + 0.5 - d;
        double h = (double) pos.getY() + 0.5 - e;
        double i = (double) pos.getZ() + 0.5 - f;
        return g * g + h * h + i * i;
    }
}
