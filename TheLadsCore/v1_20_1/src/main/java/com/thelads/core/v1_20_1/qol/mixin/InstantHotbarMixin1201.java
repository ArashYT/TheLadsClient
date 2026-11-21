package com.thelads.core.v1_20_1.qol.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.modules.InstantHotbarModule;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Instant Hotbar: a hotbar key (or mouse button) press selects its slot right here, in press order, and the click is swallowed so
 * handleKeybinds does not re-apply it by slot index next tick. Same conditions as handleKeybinds: no screen or overlay, a player,
 * not spectator (those keys drive the spectator menu), not while the creative save/load toolbar keys are held.
 */
@Mixin(KeyMapping.class)
abstract class InstantHotbarMixin1201 {
    @Shadow @Final private static Map<InputConstants.Key, KeyMapping> MAP;

    @Inject(method = "click", at = @At("HEAD"), cancellable = true, require = 0)
    private static void lads$instantSlot(InputConstants.Key key, CallbackInfo ci) {
        KeyMapping mapping = MAP.get(key);
        if (mapping == null || !InstantHotbarModule.active()) return;
        Minecraft mc = Minecraft.getInstance();
        Options options = mc.options;
        if (options == null || mc.player == null || mc.screen != null || mc.getOverlay() != null || mc.player.isSpectator()) return;
        int slot = -1;
        for (int i = 0; i < options.keyHotbarSlots.length; i++) if (options.keyHotbarSlots[i] == mapping) slot = i;
        if (slot < 0) return;
        if (com.thelads.core.v1_20_1.ladsapi.ui.LadsApiUi1201.locksKey(mapping)) return; // LadsAPI minigame hotbar lock: leave the click to the lock
        if (mc.player.isCreative() && (options.keySaveHotbarActivator.isDown() || options.keyLoadHotbarActivator.isDown())) return;
        mc.player.getInventory().selected = slot;
        InstantHotbarModule.applied(slot);
        ci.cancel();
    }
}
