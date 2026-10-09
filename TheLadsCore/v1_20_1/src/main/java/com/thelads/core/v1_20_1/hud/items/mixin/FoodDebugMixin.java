package com.thelads.core.v1_20_1.hud.items.mixin;

import com.thelads.core.v1_20_1.hud.items.Food1201;
import java.util.List;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The AppleSkin module's F3 line at the end of the left column (1.20.1 has no DebugScreenEntries; both returns are mutable lists). */
@Mixin(DebugScreenOverlay.class)
abstract class FoodDebugMixin {
    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void lads$foodLine(CallbackInfoReturnable<List<String>> callback) {
        String line = Food1201.debugLine();
        if (line != null) callback.getReturnValue().add(line);
    }
}
