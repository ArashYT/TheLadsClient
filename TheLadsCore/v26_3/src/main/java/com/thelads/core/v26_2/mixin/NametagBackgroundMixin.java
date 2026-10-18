package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.ladsapi.LadsBadge;
import com.thelads.core.v26_2.feature.NametagBadgeContext;
import com.thelads.core.v26_2.feature.NativeNicknames;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nametags: renames, background and text shadow, set on the submitted tag so its readers (Essential's icon padding) follow. */
@Mixin(SubmitNodeCollection.class)
public class NametagBackgroundMixin {
    @Unique
    private static final Identifier LADS$BADGE_TEXTURE = Identifier.fromNamespaceAndPath(
        LadsBadge.TEXTURE_NAMESPACE,
        LadsBadge.TEXTURE_PATH
    );

    @Unique
    private static final SubmitNodeCollector.CustomGeometryRenderer BADGE_NORMAL_GEOMETRY = (pose, consumer) -> {
        float s = (float) LadsBadge.NAMETAG_SIZE;
        consumer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(-1).setUv(0.0F, 0.0F).setLight(0xF000F0);
        consumer.addVertex(pose, 0.0F, s, 0.0F).setColor(-1).setUv(0.0F, 1.0F).setLight(0xF000F0);
        consumer.addVertex(pose, s, s, 0.0F).setColor(-1).setUv(1.0F, 1.0F).setLight(0xF000F0);
        consumer.addVertex(pose, s, 0.0F, 0.0F).setColor(-1).setUv(1.0F, 0.0F).setLight(0xF000F0);
    };

    @Unique
    private static final SubmitNodeCollector.CustomGeometryRenderer BADGE_SNEAKING_GEOMETRY = (pose, consumer) -> {
        float s = (float) LadsBadge.NAMETAG_SIZE;
        consumer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(0x80FFFFFF).setUv(0.0F, 0.0F).setLight(0xF000F0);
        consumer.addVertex(pose, 0.0F, s, 0.0F).setColor(0x80FFFFFF).setUv(0.0F, 1.0F).setLight(0xF000F0);
        consumer.addVertex(pose, s, s, 0.0F).setColor(0x80FFFFFF).setUv(1.0F, 1.0F).setLight(0xF000F0);
        consumer.addVertex(pose, s, 0.0F, 0.0F).setColor(0x80FFFFFF).setUv(1.0F, 0.0F).setLight(0xF000F0);
    };

    @ModifyVariable(method = "submitNameTag", at = @At("HEAD"), argsOnly = true, require = 1)
    private Component lads$rename(Component name) {
        Component renamed = NativeNicknames.rename(name);
        if (NametagBadgeContext.CURRENT_NAMETAG != null && (name == NametagBadgeContext.CURRENT_NAMETAG || name.equals(NametagBadgeContext.CURRENT_NAMETAG))) {
            NametagBadgeContext.CURRENT_NAMETAG = renamed;
        }
        return renamed;
    }

    @ModifyVariable(method = "submitNameTag", at = @At(value = "STORE", ordinal = 0), ordinal = 0, require = 1)
    private float lads$shiftTextX(float vanillaX, @Local(argsOnly = true) Component name) {
        if (NametagBadgeContext.isBadgeActive(name)) {
            return vanillaX + (LadsBadge.NAMETAG_SIZE + LadsBadge.GAP) / 2.0F;
        }
        return vanillaX;
    }

    @ModifyArg(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollection;nameTag(Lorg/joml/Matrix4f;FFLnet/minecraft/util/FormattedCharSequence;IIILnet/minecraft/client/gui/Font$DisplayMode;)Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;"), index = 6, require = 1)
    private int lads$background(int vanillaColor) {
        return NativeQualityOfLife.enabled("Nametags") && !NativeQualityOfLife.bool("Nametags", "Render Background", true) ? 0 : vanillaColor;
    }

    @Inject(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"), require = 1)
    private void lads$submitBadge(PoseStack poseStack, Vec3 nameTagAttachment, int offset, Component name,
                                  boolean seeThrough, int lightCoords, CameraRenderState camera, CallbackInfo ci,
                                  @Local(ordinal = 0) float x) {
        if (!NametagBadgeContext.isBadgeActive(name)) return;

        float badgeX = x - (LadsBadge.NAMETAG_SIZE + LadsBadge.GAP);
        float badgeY = (float) offset;

        poseStack.pushPose();
        poseStack.translate(badgeX, badgeY, 0.0F);

        SubmitNodeCollection self = (SubmitNodeCollection) (Object) this;
        if (seeThrough) {
            self.submitCustomGeometry(poseStack, RenderTypes.text(LADS$BADGE_TEXTURE), BADGE_NORMAL_GEOMETRY);
            self.submitCustomGeometry(poseStack, RenderTypes.textSeeThrough(LADS$BADGE_TEXTURE), BADGE_SNEAKING_GEOMETRY);
        } else {
            self.submitCustomGeometry(poseStack, RenderTypes.text(LADS$BADGE_TEXTURE), BADGE_SNEAKING_GEOMETRY);
        }

        poseStack.popPose();
    }

    /** Shadow on the normal pass only; the see-through pass behind walls stays flat and readable. */
    @ModifyArg(method = "nameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Content$Text;<init>(FFLnet/minecraft/util/FormattedCharSequence;ZIII)V"), index = 3, require = 1)
    private static boolean lads$shadow(boolean shadow, @Local(argsOnly = true) Font.DisplayMode mode) {
        return shadow || mode == Font.DisplayMode.NORMAL && NativeQualityOfLife.enabled("Nametags") && NativeQualityOfLife.bool("Nametags", "Text Shadow", true);
    }
}
