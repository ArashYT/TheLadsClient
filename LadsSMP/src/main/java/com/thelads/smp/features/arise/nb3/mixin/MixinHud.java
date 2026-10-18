package com.thelads.smp.features.arise.nb3.mixin;

import com.thelads.smp.features.arise.nb3.immersiveui.ImmersiveUIState;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mixin into Hud to smoothly slide the hotbar selection box between active slots.
 */
@Mixin(Hud.class)
public abstract class MixinHud {

    @ModifyArg(
            method = "extractItemHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 1
            ),
            index = 2
    )
    private int ladssmp$slideHotbarSelectionX(int x) {
        return ImmersiveUIState.getInterpolatedHotbarX(x, 0.0f);
    }
}
