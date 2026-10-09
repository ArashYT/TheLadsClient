package com.thelads.core.v1_20_1.hud.mixin;

import com.thelads.core.v1_20_1.hud.Hud1201;
import com.thelads.core.v1_20_1.hud.VoiceChat1201;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Simple Voice Chat draws nothing of its own HUD that a Lads Voice Chat element shows instead: its status icon while "Voice Chat" is
 * on, its group list while "Voice Chat Group" is on. Pseudo: skipped when Simple Voice Chat is not installed.
 */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.RenderEvents", remap = false)
public abstract class VoiceChatHudMixin {
    @Inject(method = "renderIcon", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lads$statusIcon(GuiGraphics graphics, ResourceLocation icon, CallbackInfo ci) {
        if (VoiceChat1201.loaded() && Hud1201.enabled("Voice Chat")) {
            VoiceChat1201.suppressed++;
            ci.cancel();
        }
    }
}
