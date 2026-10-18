package com.thelads.smp.features.arise.nb3.mixin;

import com.thelads.smp.features.arise.nb3.immersiveui.ImmersiveUIState;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin into AbstractContainerScreen to add gentle floating bobbing to cursor-carried items.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen {

    @ModifyVariable(
            method = "extractCarriedItem",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1
    )
    private int ladssmp$bobCarriedItemY(int mouseY) {
        return mouseY + Math.round(ImmersiveUIState.getFloatingItemBob(System.currentTimeMillis()));
    }
}
