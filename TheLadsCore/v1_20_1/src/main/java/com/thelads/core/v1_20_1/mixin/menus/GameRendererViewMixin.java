package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.v1_20_1.feature.HudEditorView1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The moment the world is drawn and the HUD is not yet: the HUD editor's game view copies the frame here. */
@Mixin(GameRenderer.class)
public abstract class GameRendererViewMixin {
    @Shadow @org.spongepowered.asm.mixin.Final private Minecraft minecraft;

    @Inject(method = "render(FJZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
    private void ladsCopyWorldForEditor(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        HudEditorView1201.copyWorld(minecraft.getMainRenderTarget());
    }
}
