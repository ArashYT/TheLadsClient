package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.entity.renderer;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.thelads.core.v1_21_11.embedded.etf.ETF;

@Mixin(EndCrystalRenderer.class)
public abstract class MixinEndCrystalRenderer {

    @Shadow @Final private static Identifier END_CRYSTAL_LOCATION;

    @ModifyArg(method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", ordinal = 0))
    private RenderType etf$modifyTexture(final RenderType renderType) {
        if (ETF.config().getConfig().canDoCustomTextures()) {
            // recreate each frame so ETF can modify
            return
                    net.minecraft.client.renderer.rendertype.RenderTypes
                            .entityCutoutNoCull(END_CRYSTAL_LOCATION);
        }
        return renderType;
    }
}

