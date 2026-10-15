package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Consolidates 'portalsgui': Prevents Nether Portals from closing open GUI screens on the client.
 */
@Mixin(Screen.class)
public abstract class MixinScreenPortalGui {
    @Inject(method = "isAllowedInPortal", at = @At("HEAD"), cancellable = true)
    private void ladssmp$allowGuiInPortals(CallbackInfoReturnable<Boolean> cir) {
        if (LadsConfig.get().features.portalsGui) {
            cir.setReturnValue(true);
        }
    }
}
