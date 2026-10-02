// Adapted from Hovering Hotbar 21.1.1 by Fuzs (MPL-2.0); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.hoveringhotbar.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.v1_21_1.embedded.hoveringhotbar.HoveringHotbar;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Gui.class)
abstract class GuiFabricMixin {

    @WrapMethod(method = {"renderHotbarAndDecorations", "renderOverlayMessage"})
    private void renderHotbarAndDecorations(GuiGraphics guiGraphics, DeltaTracker deltaTracker, Operation<Void> operation) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, -HoveringHotbar.CONFIG.getHotbarOffset(), 0.0F);
        operation.call(guiGraphics, deltaTracker);
        guiGraphics.pose().popPose();
    }

    @WrapMethod(method = "renderExperienceLevel")
    private void renderExperienceLevel(GuiGraphics guiGraphics, DeltaTracker deltaTracker, Operation<Void> operation) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, -HoveringHotbar.CONFIG.getHotbarOffset(), 0.0F);
        if (HoveringHotbar.CONFIG.moveExperienceAboveBar) {
            guiGraphics.pose().translate(0.0F, -3.0F, 0.0F);
        }

        operation.call(guiGraphics, deltaTracker);
        guiGraphics.pose().popPose();
    }
}
