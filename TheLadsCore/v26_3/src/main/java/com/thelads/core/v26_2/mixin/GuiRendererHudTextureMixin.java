package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.v26_2.feature.HudTexture;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HUD texture (HudTexture): the frame's GUI render first draws a waiting HUD build into the HUD target; the HUD renderer draws there. */
@Mixin(GuiRenderer.class)
public class GuiRendererHudTextureMixin implements HudTexture.Renderer {
    @Shadow @Final private GuiRenderState renderState;
    @Shadow @Final private FeatureRenderDispatcher featureRenderDispatcher;
    @Shadow @Final @Mutable private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void lads$hudFirst(CallbackInfo ci) {
        if (renderState == Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState) HudTexture.beforeGui((GuiRenderer) (Object) this);
    }

    @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"), require = 1)
    private RenderTarget lads$hudTarget(GameRenderer gameRenderer, Operation<RenderTarget> original) {
        return HudTexture.isHudRenderer((GuiRenderer) (Object) this) ? HudTexture.target() : original.call(gameRenderer);
    }

    @Override
    public FeatureRenderDispatcher lads$features() {
        return featureRenderDispatcher;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<Class<?>, Object> lads$pictureRenderers() {
        return (Map) pictureInPictureRenderers;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void lads$sharePictureRenderers(Map<Class<?>, Object> renderers) {
        pictureInPictureRenderers = (Map) renderers;
    }
}
