package com.thelads.core.mixin.alwayson.skinlayers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.features.alwayson.skinlayers.SkinLayersModBase;
import com.thelads.core.features.alwayson.skinlayers.SkinUtil;
import com.thelads.core.features.alwayson.skinlayers.accessor.ModelPartInjector;
import com.thelads.core.features.alwayson.skinlayers.accessor.PlayerEntityModelAccessor;
import com.thelads.core.features.alwayson.skinlayers.accessor.PlayerSettings;
import com.thelads.core.features.alwayson.skinlayers.accessor.AvatarRenderStateAccessor;
import com.thelads.core.features.alwayson.skinlayers.api.OffsetProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, AvatarRenderState, PlayerModel> {
    private boolean setupFirstpersonArms = false;

    public PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel entityModel, float f) {
        super(context, entityModel, f);
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void renderHandStart(PoseStack poseStack, SubmitNodeCollector multiBufferSource, int i, Identifier resourceLocation, ModelPart arm, boolean bl, CallbackInfo info) {
        LocalPlayer abstractClientPlayer = Minecraft.getInstance().player;
        if (abstractClientPlayer == null) return;
        ModelPart sleeve = arm == ((PlayerModel) this.getModel()).leftArm ? ((PlayerModel) this.getModel()).leftSleeve : ((PlayerModel) this.getModel()).rightSleeve;
        PlayerSettings settings = (PlayerSettings) abstractClientPlayer;
        boolean slim = ((PlayerEntityModelAccessor) this.getModel()).hasThinArms();
        ((ModelPartInjector) (Object) sleeve).setInjectedMesh(null, null);
        if (!SkinUtil.setup3dLayers(abstractClientPlayer, settings, slim)) {
            return;
        }
        this.setupFirstpersonArms = true;
        if (arm == ((PlayerModel) this.getModel()).leftArm) {
            if (SkinLayersModBase.config.enableLeftSleeve) {
                ((ModelPartInjector) (Object) sleeve).setInjectedMesh(settings.getLeftArmMesh(), slim ? OffsetProvider.FIRSTPERSON_LEFT_ARM_SLIM : OffsetProvider.FIRSTPERSON_LEFT_ARM);
            }
        } else if (SkinLayersModBase.config.enableRightSleeve) {
            ((ModelPartInjector) (Object) sleeve).setInjectedMesh(settings.getRightArmMesh(), slim ? OffsetProvider.FIRSTPERSON_RIGHT_ARM_SLIM : OffsetProvider.FIRSTPERSON_RIGHT_ARM);
        }
    }

    @WrapOperation(method = "lambda$new$0", at = @At(value = "NEW", target = "(Lnet/minecraft/client/model/geom/ModelPart;Z)Lnet/minecraft/client/model/player/PlayerModel;"))
    private static PlayerModel markArmorModelAsIgnored(ModelPart modelPart, boolean slim, Operation<PlayerModel> original) {
        PlayerModel call = (PlayerModel) original.call(new Object[]{modelPart, slim});
        ((PlayerEntityModelAccessor) call).setIgnored(true);
        return call;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void extractRenderStateInject(AbstractClientPlayer player, AvatarRenderState state, float f, CallbackInfo ci) {
        if (state instanceof AvatarRenderStateAccessor) {
            ((AvatarRenderStateAccessor) state).skinlayers$setPlayer(player);
        }
    }
}
