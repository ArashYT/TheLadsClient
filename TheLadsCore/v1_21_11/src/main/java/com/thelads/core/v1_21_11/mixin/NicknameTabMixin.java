package com.thelads.core.v1_21_11.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.v1_21_11.feature.NativeNicknames;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Nametags renames in the tab list; column widths use the same method, so they fit the new names. */
@Mixin(PlayerTabOverlay.class)
public class NicknameTabMixin {
    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"), require = 1)
    private Component lads$rename(Component name) {
        return NativeNicknames.rename(name);
    }
}
