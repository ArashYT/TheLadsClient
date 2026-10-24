package com.thelads.core.v1_20_1.qol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.qol.Qol1201;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Scrollable Tooltips: every tooltip is drawn where its positioner puts it, moved by the scroll (Qol1201.tooltip). */
@Mixin(GuiGraphics.class)
abstract class GuiGraphicsQolMixin1201 {
    @WrapOperation(method = "renderTooltipInternal", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"))
    private Vector2ic lads$scrollTooltip(ClientTooltipPositioner positioner, int screenWidth, int screenHeight, int x, int y, int width, int height,
                                         Operation<Vector2ic> original) {
        return Qol1201.tooltip(original.call(positioner, screenWidth, screenHeight, x, y, width, height), width, height, screenWidth, screenHeight);
    }
}
