package com.thelads.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.HudElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@Mixin(targets = "de.maxhenkel.voicechat.voice.client.RenderEvents", remap = false)
public class VoiceChatMixin {
    @Inject(method = "onRenderHUD", at = @At("HEAD"), require = 0, cancellable = true)
    private void preRenderHUD(GuiGraphicsExtractor graphics, float tickDelta, CallbackInfo ci) {
        for (HudElement element : HudManager.getInstance().getElements()) {
            if ("VoiceChat".equals(element.getModuleName())) {
                if (!element.isEnabled()) {
                    ci.cancel();
                    return;
                }
                graphics.pose().pushMatrix();
                graphics.pose().translate((float)element.getX(), (float)element.getY());
                graphics.pose().scale(element.getScale(), element.getScale());
                break;
            }
        }
    }

    @Inject(method = "onRenderHUD", at = @At("RETURN"), require = 0)
    private void postRenderHUD(GuiGraphicsExtractor graphics, float tickDelta, CallbackInfo ci) {
        for (HudElement element : HudManager.getInstance().getElements()) {
            if ("VoiceChat".equals(element.getModuleName())) {
                if (element.isEnabled()) {
                    graphics.pose().popMatrix();
                }
                break;
            }
        }
    }
}
