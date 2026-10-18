package com.thelads.core.v1_8_9.mixin.xaero;

import com.thelads.core.modules.BetterF3Module;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Xaero's Minimap HUD (map, waypoints overlay) steps aside under F3 like the Lads HUD (Better F3's "Hide Lads HUD"); 1.8.9 Xaero has no such setting. */
@Pseudo
@Mixin(targets = "xaero.common.interfaces.render.InterfaceRenderer", remap = false)
public abstract class XaeroHideUnderF3Mixin189 {
    @Inject(method = "renderInterfaces", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ladsHideUnderF3(CallbackInfo ci) {
        if (BetterF3Module.hidesLadsHud(Minecraft.getMinecraft().gameSettings.showDebugInfo)) ci.cancel();
    }
}
