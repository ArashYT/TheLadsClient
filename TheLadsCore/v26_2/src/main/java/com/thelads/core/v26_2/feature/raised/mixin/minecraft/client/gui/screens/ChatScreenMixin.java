// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.mixin.minecraft.client.gui.screens;

import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layer;
import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layers;
import com.thelads.core.v26_2.feature.raised.util.Translate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = ChatScreen.class, priority = -999999999)
public abstract class ChatScreenMixin {

    /**
     * Moves the {@code chat} for {@link Layer} key "minecraft:chat".
     */
    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"))
    private void startChatTranslate(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Translate.start(guiGraphics.pose(), Layers.CHAT);
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", shift = At.Shift.AFTER))
    private void endChatTranslate(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Translate.end(guiGraphics.pose(), Layers.CHAT);
    }

    /**
     * Moves the {@code chat click} for {@link Layer} key "minecraft:chat".
     */
    @ModifyArgs(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/ActiveTextCollector$ClickableStyleFinder;<init>(Lnet/minecraft/client/gui/Font;II)V"))
    private void adjustChatStyleCoordinates(Args args) {
        // Only message text moves. Suggestions and the input EditBox still receive the
        // original MouseButtonEvent in their unchanged screen coordinates.
        args.set(1, args.<Integer>get(1) - Translate.getX(Layers.CHAT));
        args.set(2, args.<Integer>get(2) - Translate.getY(Layers.CHAT));
    }

}
