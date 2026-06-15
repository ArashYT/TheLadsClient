package com.thelads.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Coerce;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.HudElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Field;

// Xaero's HudRenderer.renderModule receives a ModuleRenderContext whose x/y
// fields determine where the *actual* minimap image is drawn via Xaero's own
// render pipeline (which bypasses the GL matrix stack). Translating the pose
// only moves the text overlay, not the map. We override context.x/y directly.
@Mixin(targets = "xaero.hud.render.HudRenderer", remap = false)
public class XaeroMinimapMixin {

    @Inject(
        method = "renderModule(Lxaero/hud/module/HudModule;Lxaero/hud/Hud;Lxaero/hud/render/module/ModuleRenderContext;Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V",
        at = @At("HEAD"), require = 0, cancellable = true
    )
    private void preRenderModule(
        @Coerce Object module, @Coerce Object hud,
        @Coerce Object context, GuiGraphicsExtractor graphics,
        float tickDelta, CallbackInfo ci
    ) {
        for (HudElement element : HudManager.getInstance().getElements()) {
            if (!"XaeroMinimap".equals(element.getModuleName())) continue;

            if (!element.isEnabled()) {
                ci.cancel();
                return;
            }
            // Override ModuleRenderContext.x/y so the minimap image moves, not just the overlay text.
            try {
                Field xf = context.getClass().getField("x");
                Field yf = context.getClass().getField("y");
                xf.set(context, element.getX());
                yf.set(context, element.getY());
            } catch (Exception ignored) {}
            break;
        }
    }
}
