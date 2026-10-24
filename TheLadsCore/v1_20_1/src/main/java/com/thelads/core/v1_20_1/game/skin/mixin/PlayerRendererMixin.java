package com.thelads.core.v1_20_1.game.skin.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.game.skin.SkinLayers1201;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3D skin layers on players: the cuboid layer, the flat parts it replaces, and the first-person sleeve (see SkinLayers1201). */
@Mixin(PlayerRenderer.class)
abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private PlayerRendererMixin() { super(null, null, 0.0f); }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void lads$skinLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo callback) {
        RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer = SkinLayers1201.layer(this);
        if (layer != null) addLayer(layer);
    }

    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void lads$skinLayerParts(AbstractClientPlayer player, CallbackInfo callback) {
        SkinLayers1201.prepare((PlayerRenderer) (Object) this, player);
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void lads$skinLayerHandBegin(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
                                         ModelPart sleeve, CallbackInfo callback) {
        SkinLayers1201.beginHand();
    }

    @Inject(method = "renderHand", at = @At("TAIL"))
    private void lads$skinLayerHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
                                    ModelPart sleeve, CallbackInfo callback) {
        SkinLayers1201.endHand(pose, buffers, light, player, sleeve, getModel());
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void lads(AbstractClientPlayer entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity != null && com.thelads.core.client.hypixel.CrowdPlayerHider.shouldHidePitPlayer(entity == net.minecraft.client.Minecraft.getInstance().player, entity.getX(), entity.getY(), entity.getZ())) {
            ci.cancel();
        }
    }
}
