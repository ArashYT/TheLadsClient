/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets={"de.maxhenkel.voicechat.voice.client.RenderEvents"}, remap=false)
public class VoiceChatMixin {
    @Inject(method={"onRenderHUD"}, at={@At(value="HEAD")}, require=0, cancellable=true)
    private void preRenderHUD(GuiGraphicsExtractor graphics, float tickDelta, CallbackInfo ci) {
        for (HudElement element : HudManager.getInstance().getElements()) {
            if (!"VoiceChat".equals(element.getModuleName())) continue;
            if (!element.isEnabled()) {
                ci.cancel();
                return;
            }
            graphics.pose().pushMatrix();
            graphics.pose().translate(element.getX(), element.getY());
            graphics.pose().scale(element.getScale(), element.getScale());
            break;
        }
    }

    @Inject(method={"onRenderHUD"}, at={@At(value="RETURN")}, require=0)
    private void postRenderHUD(GuiGraphicsExtractor graphics, float tickDelta, CallbackInfo ci) {
        for (HudElement element : HudManager.getInstance().getElements()) {
            if (!"VoiceChat".equals(element.getModuleName())) continue;
            if (!element.isEnabled()) break;
            graphics.pose().popMatrix();
            break;
        }
    }
}

