package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeClientTools;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Modify display components only. Signed packets, signatures and report logs stay intact. */
@Mixin(ChatComponent.class)
public abstract class ClientToolsChatMixin {
    @Unique private static long ladsLastMessageTime;

    @ModifyVariable(method = {"addClientSystemMessage", "addServerSystemMessage", "addPlayerMessage"},
        at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 3)
    private Component ladsTimestamp(Component content) {
        ladsLastMessageTime = System.currentTimeMillis();
        return NativeClientTools.timestamp(content);
    }

    @Inject(method = "getWidth(D)I", at = @At("HEAD"), cancellable = true)
    private static void ladsChatWidth(double widthOption, CallbackInfoReturnable<Integer> cir) {
        if (NativeQualityOfLife.enabled("Chat")) {
            cir.setReturnValue((int) NativeQualityOfLife.number("Chat", "Chat Width", 320));
        }
    }

    @Inject(method = "getHeight(D)I", at = @At("HEAD"), cancellable = true)
    private static void ladsChatHeight(double heightOption, CallbackInfoReturnable<Integer> cir) {
        if (NativeQualityOfLife.enabled("Chat")) {
            cir.setReturnValue((int) NativeQualityOfLife.number("Chat", "Chat Height", 180));
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"), cancellable = true)
    private void ladsChatRender(GuiGraphicsExtractor g, Font font, int tick, int mouseX, int mouseY, ChatComponent.DisplayMode mode, boolean focused, CallbackInfo ci) {
        if (!mode.foreground && !NativeQualityOfLife.bool("Chat", "Chat Background", true)) {
            ci.cancel();
            return;
        }
        if (NativeQualityOfLife.bool("Chat", "Message Animations", true)) {
            long elapsed = System.currentTimeMillis() - ladsLastMessageTime;
            if (elapsed >= 0 && elapsed < 250) {
                float progress = elapsed / 250f;
                float slide = (1f - (float) Math.sin(progress * Math.PI / 2.0)) * -16f;
                g.pose().translate(slide, 0f);
            }
        }
    }
}
