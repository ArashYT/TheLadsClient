package com.thelads.core.v1_20_1.mixin.social;

import com.thelads.core.v1_20_1.social.SocialBridge1201;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept client-side /lads:social <id> chat click actions, ensuring they never leave the client.
 */
@Mixin(Screen.class)
public abstract class ChatClickActionMixin {
    @Inject(method = "handleComponentClicked(Lnet/minecraft/network/chat/Style;)Z", at = @At("HEAD"), cancellable = true)
    private void ladsInterceptSocialAction(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (style != null) {
            ClickEvent click = style.getClickEvent();
            if (click != null && click.getValue() != null) {
                String val = click.getValue().trim();
                if (val.startsWith("/lads:social ") || val.startsWith("lads:social ")) {
                    int prefix = val.startsWith("/") ? "/lads:social ".length() : "lads:social ".length();
                    String actionId = val.substring(prefix).trim();
                    SocialBridge1201.handleChatAction(actionId);
                    cir.setReturnValue(true);
                }
            }
        }
    }
}
