package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.HotbarScrollHelper;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ScrollTweaksModule;
import net.minecraft.client.ScrollWheelHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScrollWheelHandler.class)
public abstract class ScrollWheelHandlerMixin {
    @Inject(method = "getNextScrollWheelSelection(DII)I", at = @At("HEAD"), cancellable = true)
    private static void lads$scrollHotbar(double scrollDelta, int currentSelection, int totalSelections, CallbackInfoReturnable<Integer> cir) {
        Module m = ModuleManager.getInstance().getModule(ScrollTweaksModule.NAME);
        if (m instanceof ScrollTweaksModule stm && stm.isEnabled()) {
            int dir = (int) Math.signum(scrollDelta);
            cir.setReturnValue(HotbarScrollHelper.computeTargetSlot(currentSelection, dir, stm.isScrollInverted(), stm.isNoWrap()));
        }
    }
}
