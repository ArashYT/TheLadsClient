package com.thelads.core.v26_2.embedded.etf.mixin.mixins.entity.renderer;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.thelads.core.v26_2.embedded.etf.ETF;
import com.thelads.core.v26_2.embedded.etf.utils.ETFUtils2;






@Mixin(EnderDragonRenderer.class)
public abstract class MixinEnderDragonEntityRenderer extends
EntityRenderer<EnderDragon, net.minecraft.client.renderer.entity.state.EnderDragonRenderState>
{


    @Final
    @Shadow
    private static Identifier DRAGON_EYES_LOCATION;      // = new Identifier("textures/entity/enderdragon/dragon_eyes.png");

    @Final
    @Shadow
    private static RenderType EYES;     //= RenderLayer.getEyes(EYE_TEXTURE);

    @SuppressWarnings("unused")
    protected MixinEnderDragonEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }


    @ModifyArg(method = "submit(Lnet/minecraft/client/renderer/entity/state/EnderDragonRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
            ))
    private RenderType etf$returnAlteredTexture2(RenderType texturedRenderLayer) { return getType(texturedRenderLayer); }

    @Unique
    private static @Nullable RenderType getType(final RenderType texturedRenderLayer) {
        if (ETF.config().getConfig().canDoCustomTextures()) {
            try {
                if (EYES.equals(texturedRenderLayer)) {
                    return
                            net.minecraft.client.renderer.rendertype.RenderTypes
                                    .eyes(DRAGON_EYES_LOCATION);
                }
            } catch (Exception e) {
                ETFUtils2.logError(e.toString(), false);
            }
        }
        return texturedRenderLayer;
    }

}


