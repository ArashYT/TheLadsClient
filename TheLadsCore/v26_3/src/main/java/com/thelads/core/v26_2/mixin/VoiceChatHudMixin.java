package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.VoiceChatIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Simple Voice Chat draws nothing of its own HUD that a Lads Voice Chat element shows instead (1.7.0): its status icon while
 * "Voice Chat" is on, its group list while "Voice Chat Group" is on. Pseudo: skipped when Simple Voice Chat is not installed.
 */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.RenderEvents", remap = false)
public class VoiceChatHudMixin {
    @Inject(method = "renderIcon", at = @At("HEAD"), cancellable = true, remap = false)
    private void lads$statusIcon(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.resources.Identifier icon, CallbackInfo ci) {
        if (VoiceChatIntegration.loaded() && NativeQualityOfLife.enabled("Voice Chat")) ci.cancel();
    }

    @Pseudo
    @Mixin(targets = "de.maxhenkel.voicechat.voice.client.GroupChatManager", remap = false)
    public static class Group {
        @Inject(method = "renderIcons", at = @At("HEAD"), cancellable = true, remap = false)
        private static void lads$groupList(net.minecraft.client.gui.GuiGraphicsExtractor graphics, CallbackInfo ci) {
            if (VoiceChatIntegration.loaded() && NativeQualityOfLife.enabled("Voice Chat Group")) ci.cancel();
        }
    }
}
