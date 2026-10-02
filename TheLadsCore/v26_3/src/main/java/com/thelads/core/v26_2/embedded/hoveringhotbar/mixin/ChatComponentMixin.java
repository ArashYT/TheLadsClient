// Adapted from Hovering Hotbar 26.3.0 by Fuzs (MPL-2.0); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.hoveringhotbar.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.v26_2.embedded.hoveringhotbar.HoveringHotbar;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {

    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
                           at = @At(value = "CONSTANT", args = "intValue=40"),
                           slice = @Slice(to = @At(value = "INVOKE",
                                                   target = "Lnet/minecraft/client/Options;chatOpacity()Lnet/minecraft/client/OptionInstance;")))
    private int extractRenderState(int bottomMargin) {
        return bottomMargin + HoveringHotbar.CONFIG.getHotbarOffset();

    }
}
