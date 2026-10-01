// Adapted from Hovering Hotbar 21.1.1 by Fuzs (MPL-2.0); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.hoveringhotbar.mixin;

import net.minecraft.client.gui.Gui;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(Gui.class)
abstract class GuiMixin {

    @ModifyArg(method = "renderItemHotbar",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"),
               index = 4,
               slice = @Slice(from = @At(value = "FIELD",
                                         target = "Lnet/minecraft/client/gui/Gui;HOTBAR_SELECTION_SPRITE:Lnet/minecraft/resources/ResourceLocation;",
                                         opcode = Opcodes.GETSTATIC),
                              to = @At(value = "FIELD",
                                       target = "Lnet/minecraft/client/gui/Gui;HOTBAR_OFFHAND_LEFT_SPRITE:Lnet/minecraft/resources/ResourceLocation;",
                                       opcode = Opcodes.GETSTATIC)))
    private int extractItemHotbar(int height) {
        return height == 23 ? 24 : height;
    }
}
