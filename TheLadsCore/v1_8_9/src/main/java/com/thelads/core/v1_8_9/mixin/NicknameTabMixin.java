package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Nametags189;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nametags renames in the tab list, as 26.x; column widths use the same method, so they fit the new names. */
@Mixin(GuiPlayerTabOverlay.class)
public abstract class NicknameTabMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 1)
    private void ladsRename(NetworkPlayerInfo info, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(Nametags189.rename(cir.getReturnValue()));
    }
}
