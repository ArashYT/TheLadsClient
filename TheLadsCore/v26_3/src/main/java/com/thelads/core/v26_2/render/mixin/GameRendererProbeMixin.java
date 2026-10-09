package com.thelads.core.v26_2.render.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.v26_2.render.RenderProbe26;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** QA (1.8.5 render capture): each frame's extract and render CPU time while RenderProbe26 measures, and its finished frame. */
@Mixin(GameRenderer.class)
public abstract class GameRendererProbeMixin {
    @Shadow private RenderTarget mainRenderTarget;
    @Shadow @Final private GuiRenderer guiRenderer;

    @Inject(method = "extract", at = @At("HEAD"))
    private void lads$extractStart(CallbackInfo ci) { RenderProbe26.phaseStart(); }

    @Inject(method = "extract", at = @At("RETURN"))
    private void lads$extractEnd(CallbackInfo ci) { RenderProbe26.phaseEnd(false, null); }

    @Inject(method = "render", at = @At("HEAD"))
    private void lads$renderStart(CallbackInfo ci) {
        if (RenderProbe26.invalidateGui) {
            RenderProbe26.invalidateGui = false;
            ((GuiRendererProbeAccess) guiRenderer).lads$invalidateItemAtlas();
        }
        RenderProbe26.phaseStart();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void lads$renderEnd(CallbackInfo ci) { RenderProbe26.phaseEnd(true, mainRenderTarget); }
}
