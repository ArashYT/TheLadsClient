/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Coerce
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import java.lang.reflect.Field;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets={"xaero.hud.render.HudRenderer"}, remap=false)
public class XaeroMinimapMixin {
    @Inject(method={"renderModule(Lxaero/hud/module/HudModule;Lxaero/hud/Hud;Lxaero/hud/render/module/ModuleRenderContext;Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V"}, at={@At(value="HEAD")}, require=0, cancellable=true)
    private void preRenderModule(@Coerce Object module, @Coerce Object hud, @Coerce Object context, GuiGraphicsExtractor graphics, float tickDelta, CallbackInfo ci) {
        for (HudElement element : HudManager.getInstance().getElements()) {
            if (!"XaeroMinimap".equals(element.getModuleName())) continue;
            if (!element.isEnabled()) {
                ci.cancel();
                return;
            }
            try {
                Field xf = context.getClass().getField("x");
                Field yf = context.getClass().getField("y");
                xf.set(context, element.getX());
                yf.set(context, element.getY());
            }
            catch (Exception exception) {}
            break;
        }
    }
}

