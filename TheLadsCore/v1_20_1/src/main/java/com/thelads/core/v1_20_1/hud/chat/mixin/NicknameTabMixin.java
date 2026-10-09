package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.thelads.core.v1_20_1.hud.chat.NativeNicknames1201;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nametags renames in the tab list; column widths use the same method, so they fit the new names. */
@Mixin(PlayerTabOverlay.class)
public abstract class NicknameTabMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 1)
    private void lads$rename(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(NativeNicknames1201.rename(cir.getReturnValue()));
    }
}
