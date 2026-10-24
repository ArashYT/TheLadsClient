package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.social.SocialBridge26;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenChatClickMixin {
    @Inject(
        method = "clickCommandAction(Lnet/minecraft/client/player/LocalPlayer;Ljava/lang/String;Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private static void ladsInterceptChatAction(LocalPlayer player, String command, Screen screen, CallbackInfo ci) {
        if (command != null && (command.startsWith("/lads:social ") || command.startsWith("lads:social "))) {
            String id = command.substring(command.indexOf("lads:social ") + "lads:social ".length()).trim();
            SocialBridge26.onChatActionClicked(id);
            ci.cancel();
        }
    }
}
