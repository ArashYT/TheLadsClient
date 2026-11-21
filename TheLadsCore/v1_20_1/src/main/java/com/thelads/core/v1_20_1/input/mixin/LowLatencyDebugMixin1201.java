package com.thelads.core.v1_20_1.input.mixin;

import com.thelads.core.modules.ExtraModules;
import java.util.List;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Low Latency module's debug overlay: its lines at the end of the F3 screen's left column. */
@Mixin(DebugScreenOverlay.class)
public class LowLatencyDebugMixin1201 {
    @Inject(method = "getGameInformation", at = @At("RETURN"), require = 0)
    private void lads$lowLatency(CallbackInfoReturnable<List<String>> cir) {
        List<String> lines = ExtraModules.LOW_LATENCY.overlayLines();
        if (!lines.isEmpty()) cir.getReturnValue().addAll(lines);
    }
}
