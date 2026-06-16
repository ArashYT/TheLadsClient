package com.thelads.core.mixin.alwayson.skinlayers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.features.alwayson.skinlayers.accessor.ModelPartInjector;
import com.thelads.core.features.alwayson.skinlayers.api.Mesh;
import com.thelads.core.features.alwayson.skinlayers.api.OffsetProvider;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {ModelPart.class}, priority = 300)
public class ModelPartMixin implements ModelPartInjector {
    @Shadow
    boolean visible;
    @Shadow
    private Map<String, ModelPart> children;
    private Mesh injectedMesh = null;
    private OffsetProvider offsetProvider = null;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"), cancellable = true)
    public void render(PoseStack poseStack, VertexConsumer vertexConsumer, int light, int overlay, int color, CallbackInfo ci) {
        if (this.visible && this.injectedMesh != null) {
            poseStack.pushPose();
            this.translateAndRotate(poseStack);
            
            poseStack.pushPose();
            this.offsetProvider.applyOffset(poseStack, this.injectedMesh);
            this.injectedMesh.render((ModelPart) (Object) this, poseStack, vertexConsumer, light, overlay, color);
            poseStack.popPose();
            
            if (this.children != null) {
                for (ModelPart child : this.children.values()) {
                    child.render(poseStack, vertexConsumer, light, overlay, color);
                }
            }
            
            poseStack.popPose();
            ci.cancel();
        }
    }

    @Override
    public void setInjectedMesh(Mesh mesh, OffsetProvider offsetProvider) {
        this.injectedMesh = mesh;
        this.offsetProvider = offsetProvider;
    }

    @Shadow
    public void translateAndRotate(PoseStack poseStack) {
    }

    @Override
    public void prepareTranslateAndRotate(PoseStack poseStack) {
        this.translateAndRotate(poseStack);
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public Mesh getInjectedMesh() {
        return this.injectedMesh;
    }

    @Override
    public OffsetProvider getOffsetProvider() {
        return this.offsetProvider;
    }
}
