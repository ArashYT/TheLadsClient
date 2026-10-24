package com.thelads.core.v26_2.hudextras.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.modules.TabListModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tab list Key Mode Toggle and Animated Open/Close: the player list key reads as held while the list is toggled open or still closing,
 * for vanilla's Hud and anything else that asks. Only that key; nothing is pressed or sent.
 */
@Mixin(KeyMapping.class)
public class TabKeyMixin26 {
    @ModifyReturnValue(method = "isDown()Z", at = @At("RETURN"))
    private boolean lads$tab(boolean down) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null || (Object) this != mc.options.keyPlayerList) return down;
        TabListModule tab = HudExtras.TAB;
        return tab.toggle || tab.animated ? tab.tabKey(down, System.nanoTime()) : down;
    }
}
