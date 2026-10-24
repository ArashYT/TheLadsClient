package com.thelads.core.v1_8_9.hudextras.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.modules.TabListModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tab list Key Mode Toggle and Animated Open/Close: the player list key reads as held while the list is toggled open or still closing,
 * for Forge's HUD and anything else that asks. Only that key; nothing is pressed or sent.
 */
@Mixin(KeyBinding.class)
public class TabKeyMixin189 {
    @ModifyReturnValue(method = "isKeyDown", at = @At("RETURN"))
    private boolean lads$tab(boolean down) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || (Object) this != mc.gameSettings.keyBindPlayerList) return down;
        TabListModule tab = HudExtras.TAB;
        return tab.toggle || tab.animated ? tab.tabKey(down, System.nanoTime()) : down;
    }
}
