package com.thelads.core.v26_2.qol.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.modules.InstantHotbarModule;
import java.util.List;
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
 * A key shared with another binding is left to vanilla.
 */
@Mixin(KeyMapping.class)
abstract class InstantHotbarMixin26 {
    @Shadow @Final private static Map<InputConstants.Key, List<KeyMapping>> MAP;

    @Inject(method = "click", at = @At("HEAD"), cancellable = true, require = 0)
    private static void lads$instantSlot(InputConstants.Key key, CallbackInfo ci) {
        List<KeyMapping> mappings = MAP.get(key);
        if (mappings == null || !InstantHotbarModule.active()) return;
        Minecraft mc = Minecraft.getInstance();
        Options options = mc.options;
        if (options == null || mc.player == null || mc.gui.screen() != null || mc.gui.overlay() != null || mc.player.isSpectator()) return;
        int slot = -1;
        for (KeyMapping m : mappings) {
            int idx = java.util.Arrays.asList(options.keyHotbarSlots).indexOf(m);
            if (idx >= 0) { if (slot >= 0) return; slot = idx; }
            else if (!m.getName().startsWith("key.debug.")) return;
        }
        if (slot < 0) return;
        // LadsAPI minigame hotbar lock and Lads spectator POV drain these clicks themselves: leave them to those
        if (com.thelads.core.v26_2.feature.Spectate26.locksInput() || com.thelads.core.v26_2.ladsapi.ui.LadsApiUi26.locksKey(options.keyHotbarSlots[slot])) return;
        if (mc.player.isCreative() && (options.keySaveHotbarActivator.isDown() || options.keyLoadHotbarActivator.isDown())) return;
        mc.player.getInventory().setSelectedSlot(slot);
        InstantHotbarModule.applied(slot);
        ci.cancel();
    }
}
