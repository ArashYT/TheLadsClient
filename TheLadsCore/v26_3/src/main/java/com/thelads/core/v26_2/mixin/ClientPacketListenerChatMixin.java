package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.social.SocialBridge26;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerChatMixin {
    @Inject(method = "sendCommand(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsFilterOutSocialCommand(String command, CallbackInfo ci) {
        if (command != null && (command.startsWith("/lads:social ") || command.startsWith("lads:social "))) {
            String id = command.substring(command.indexOf("lads:social ") + "lads:social ".length()).trim();
            SocialBridge26.onChatActionClicked(id);
            ci.cancel();
        }
    }
}
