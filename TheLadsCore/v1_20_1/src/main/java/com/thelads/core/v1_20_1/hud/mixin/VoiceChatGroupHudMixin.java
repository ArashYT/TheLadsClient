package com.thelads.core.v1_20_1.hud.mixin;

import com.thelads.core.v1_20_1.hud.Hud1201;
import com.thelads.core.v1_20_1.hud.VoiceChat1201;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Simple Voice Chat's group list is left to the Lads "Voice Chat Group" element while that is on. Pseudo: skipped without the mod. */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.GroupChatManager", remap = false)
public abstract class VoiceChatGroupHudMixin {
    @Inject(method = "renderIcons", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void lads$groupList(GuiGraphics graphics, CallbackInfo ci) {
        if (VoiceChat1201.loaded() && Hud1201.enabled("Voice Chat Group")) {
            VoiceChat1201.suppressed++;
            ci.cancel();
        }
    }
}
