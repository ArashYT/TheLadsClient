package com.thelads.core.v26_2.embedded.emf.mixin.mixins.rendering.model;



import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.DragonFireballRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.emf.EMF;
import com.thelads.core.v26_2.embedded.emf.EMFManager;
import com.thelads.core.v26_2.embedded.emf.models.parts.EMFModelPartRoot;
import com.thelads.core.v26_2.embedded.emf.utils.EMFUtils;

import java.util.List;
import java.util.Map;

@Mixin(DragonFireballRenderer.class)
public abstract class Mixin_EnderDragonFireball_Model {


    @Shadow
    @Final
    private static RenderType RENDER_TYPE;
    @Unique
    private static final ModelLayerLocation emf$fireball =
            new ModelLayerLocation(EMFUtils.res("minecraft", "dragon"), "fireball");

    @Unique private EntityModel<net.minecraft.client.renderer.entity.state.EntityRenderState> fireball = null;


    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void emf$createModel(EntityRendererProvider.Context context, CallbackInfo ci) {
        if (EMF.testForForgeLoadingError()) return;

        var possibleModel = EMFManager.getInstance().injectIntoModelRootGetter(emf$fireball,
                new ModelPart(List.of(),
                        Map.of("fireball", new ModelPart(List.of(), Map.of()))));

        if (possibleModel instanceof EMFModelPartRoot) {
            fireball = new EntityModel<>(possibleModel) {};
        }
    }


    private static final String RENDER_METHOD = "submit";

    @Inject(method = RENDER_METHOD, at = @At("HEAD"), cancellable = true)
    private void emf$renderModel(final CallbackInfo ci,
                               @Local(argsOnly = true) PoseStack poseStack,
                               @Local(argsOnly = true) net.minecraft.client.renderer.entity.state.EntityRenderState entityRenderState,
                               @Local(argsOnly = true) net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector
    ) {
        if (fireball != null) {
            submitNodeCollector.submitModel(fireball, entityRenderState, poseStack,
                    RENDER_TYPE, entityRenderState.lightCoords, OverlayTexture.NO_OVERLAY,
                    -1, null, 0, null);

            ci.cancel();
        }
    }

}



