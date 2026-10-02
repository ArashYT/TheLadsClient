package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins;

import org.spongepowered.asm.mixin.Mixin;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.state.EMFState;

import net.minecraft.client.model.PlayerModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.emf.EMF;
import com.thelads.core.v1_21_1.embedded.emf.models.parts.EMFModelPartRoot;
import com.thelads.core.v1_21_1.embedded.emf.EMFManager;
import com.thelads.core.v1_21_1.embedded.emf.utils.EMFEntity;
import com.thelads.core.v1_21_1.embedded.emf.utils.EMFUtils;

import net.minecraft.tags.ItemTags;
@Mixin(value = net.minecraft.client.model.PlayerModel.class, priority = 2000)//higher priority to allow other mods to cancel
public abstract class MixinCapeModels {
    // unneeded now
  @Unique
    private ModelPart emf$capeModelPart = null;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void setEmf$Model(CallbackInfo ci) {
        if (EMF.testForForgeLoadingError()) return;

        var layer = new ModelLayerLocation(EMFUtils.res("minecraft", "player"), "cape");
        ModelPart capeModel = EMFManager.getInstance().injectIntoModelRootGetter(layer, PlayerModel.createMesh(CubeDeformation.NONE,false).getRoot().bake(64,64));

        //separate cape model, if it has a custom jem model
        if (capeModel instanceof EMFModelPartRoot && capeModel.hasChild("cloak")) {
            emf$capeModelPart = capeModel.getChild("cloak");
        }
    }

    @Inject(method = "renderCloak",
            at = @At("HEAD"),
            cancellable = true)
    private void emf$RenderCustomModelOnly(final PoseStack poseStack, final VertexConsumer vertexConsumer, final int i, final int j, final CallbackInfo ci) {
        if (emf$capeModelPart != null) {

            //reset to last pose
            poseStack.popPose();
            poseStack.pushPose();

            EMFEntity emfEntity = EMFState.emfEntity();
            if (!(emfEntity instanceof Player)) return;
            Player player = (Player) emfEntity;

            //if chestplate move cape back
            if (
                 player.getItemBySlot(EquipmentSlot.CHEST).is(ItemTags.CHEST_ARMOR)
            ) {
                poseStack.translate(0.0f, -0.0625f, 0.1875f);
            }else{
                poseStack.translate(0.0f, 0.0f, 0.125f);
            }
            //flip cape
            poseStack.mulPose(Axis.YP.rotationDegrees(180));

            emf$capeModelPart.render(poseStack, vertexConsumer, i, j);

            ci.cancel();
        }
    }

}
