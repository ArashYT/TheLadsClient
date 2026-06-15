package com.thelads.core.mixin;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.BetterF3Module;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {

    @Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true)
    private void modifyLeftText(CallbackInfoReturnable<List<String>> cir) {
        BetterF3Module m = (BetterF3Module) ModuleManager.getInstance().getModule("BetterF3");
        if (m != null && m.isEnabled()) {
            cir.setReturnValue(m.filterLeftText(cir.getReturnValue()));
        }
    }

    @Inject(method = "getSystemInformation", at = @At("RETURN"), cancellable = true)
    private void modifyRightText(CallbackInfoReturnable<List<String>> cir) {
        BetterF3Module m = (BetterF3Module) ModuleManager.getInstance().getModule("BetterF3");
        if (m != null && m.isEnabled()) {
            cir.setReturnValue(m.filterRightText(cir.getReturnValue()));
        }
    }
}
