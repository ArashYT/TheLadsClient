// Adapted from Hovering Hotbar 26.3.0 by Fuzs (MPL-2.0); modified by The Lads: repackaged into Lads Core; ported to Minecraft 1.20.1.
package com.thelads.core.v1_20_1.embedded.hoveringhotbar.mixin;

import com.thelads.core.v1_20_1.embedded.hoveringhotbar.HoveringHotbar;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The chat sits 40 pixels above the screen bottom: lift it with the hotbar. 1.20.1 repeats that 40 in the two places that map the
 * mouse onto chat lines, which must move with it or clicks and hover effects would land on the wrong line.
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {

    @ModifyConstant(method = "render", constant = @Constant(intValue = 40))
    private int hoveringhotbar$render(int bottomMargin) {
        HoveringHotbar.CHAT_LINES.incrementAndGet();
        return bottomMargin + HoveringHotbar.CONFIG.getHotbarOffset();
    }

    @ModifyConstant(method = "screenToChatY", constant = @Constant(doubleValue = 40.0))
    private double hoveringhotbar$screenToChatY(double bottomMargin) {
        return bottomMargin + HoveringHotbar.CONFIG.getHotbarOffset();
    }

    @ModifyConstant(method = "handleChatQueueClicked", constant = @Constant(doubleValue = 40.0))
    private double hoveringhotbar$chatQueueClicked(double bottomMargin) {
        return bottomMargin + HoveringHotbar.CONFIG.getHotbarOffset();
    }
}
