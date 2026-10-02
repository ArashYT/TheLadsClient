// Adapted from Hovering Hotbar 21.1.1 by Fuzs (MPL-2.0); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.hoveringhotbar.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.v1_21_1.embedded.hoveringhotbar.HoveringHotbar;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {

    @ModifyExpressionValue(method = "render",
                           at = @At(value = "CONSTANT", args = "intValue=40"),
                           slice = @Slice(to = @At(value = "INVOKE",
                                                   target = "Lnet/minecraft/client/Options;chatOpacity()Lnet/minecraft/client/OptionInstance;")))
    private int render(int bottomMargin) {
        return bottomMargin + HoveringHotbar.CONFIG.getHotbarOffset();

    }
}
