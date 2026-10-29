package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGameBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Xaero's World Map (its full map shows the explored terrain, cave mode and the radar) stays closed where the minimap's server rules
 * keep minimaps off (banned, or unknown rules and not bypassed), as the minimap does (Minimap1201). The screen's class name is all it
 * needs from Xaero, so no Xaero update can open it again.
 */
@Mixin(Minecraft.class)
public abstract class XaeroWorldMapGateMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$noWorldMap(Screen screen, CallbackInfo ci) {
        if (screen == null || !"xaero.map.gui.GuiMap".equals(screen.getClass().getName()) || !HypixelSafetyManager.isMinimapBanned()) return;
        ci.cancel();
        LadsGameBridge.get().displayChatMessage(HypixelSafetyManager.getBlockedNotice("XaeroWorldMap"));
    }
}
