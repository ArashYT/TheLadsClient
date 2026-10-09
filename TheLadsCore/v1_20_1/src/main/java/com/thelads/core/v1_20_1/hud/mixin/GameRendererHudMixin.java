package com.thelads.core.v1_20_1.hud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.hud.HudPipeline1201;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Lads HUD is drawn right after the whole in-game GUI, from the one call GameRenderer makes to it. That call is the same on
 * plain Fabric and under Forge, whose ForgeGui replaces Gui.render itself (a TAIL injection into Gui.render never runs there).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererHudMixin {
    @WrapOperation(method = "render(FJZ)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V"), require = 1)
    private void lads$hud(Gui gui, GuiGraphics graphics, float partialTick, Operation<Void> original) {
        HudPipeline1201.beforeGui(graphics);
        original.call(gui, graphics, partialTick);
        HudPipeline1201.afterGui(graphics);
    }

    /** The HUD editor's game view is this frame's world, taken just before the screen draws over it. */
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"), require = 1)
    private void lads$beforeScreen(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo callback) {
        com.thelads.core.v1_20_1.hud.HudEditorView1201.capture();
    }
}
