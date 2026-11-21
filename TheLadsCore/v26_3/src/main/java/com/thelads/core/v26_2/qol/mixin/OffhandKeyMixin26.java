package com.thelads.core.v26_2.qol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.modules.OffhandKeyModule;
import com.thelads.core.v26_2.feature.NativeKeyBindings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offhand Key (gated by HypixelSafetyManager through OffhandKeyModule.active): the Use key tries the main hand only, the
 * "Use Offhand Item" key runs the same startUseItem for the off hand only, with vanilla's click loop, hold-to-keep-using and right-click delay.
 */
@Mixin(Minecraft.class)
abstract class OffhandKeyMixin26 {
    @Shadow private int rightClickDelay;
    @Shadow public LocalPlayer player;
    @Shadow public MultiPlayerGameMode gameMode;
    @Shadow @Final public Options options;
    @Shadow protected abstract void startUseItem();

    /** The hand startUseItem is limited to while the off-hand key drives it; null otherwise. */
    @Unique private InteractionHand lads$hand;
    @Unique private boolean lads$useSwapped, lads$useWasDown;
    /** Set once the handleKeybinds hook ran: the Use key is only limited to the main hand while the off-hand key can work too. */
    @Unique private boolean lads$ready;

    @Inject(method = "handleKeybinds", at = @At("HEAD"), require = 0)
    private void lads$offhandKey(CallbackInfo ci) {
        lads$useSwapped = false;
        if (player == null || gameMode == null || !OffhandKeyModule.active()) return;
        KeyMapping off = NativeKeyBindings.OFFHAND_USE;
        lads$ready = true;
        if (com.thelads.core.v26_2.ladsapi.ui.LadsApiUi26.locksKey(options.keyUse) || com.thelads.core.v26_2.feature.Spectate26.locksInput()) { // LadsAPI minigame Use lock / Lads spectator POV: the off-hand key must not get around them
            while (off.consumeClick()) { /* drained */ }
            return;
        }
        // start first (vanilla's own start comes after this hook and would see Use up), then swap Use for the off-hand key
        if (!player.isUsingItem()) {
            while (off.consumeClick()) lads$useOffhand();
            if (off.isDown() && rightClickDelay == 0 && !player.isUsingItem()) lads$useOffhand();
        }
        if (player.isUsingItem()) {
            // vanilla releases a held item when Use is up: for an off-hand item that means the off-hand key
            if (player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                lads$useWasDown = options.keyUse.isDown();
                options.keyUse.setDown(off.isDown());
                lads$useSwapped = true;
            }
            while (off.consumeClick()) { /* drained, as vanilla drains Use while an item is in use */ }
        }
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"), require = 0)
    private void lads$offhandKeyDone(CallbackInfo ci) {
        if (lads$useSwapped) options.keyUse.setDown(lads$useWasDown);
        lads$useSwapped = false;
    }

    @Unique
    private void lads$useOffhand() {
        OffhandKeyModule.used();
        lads$hand = InteractionHand.OFF_HAND;
        try { startUseItem(); } finally { lads$hand = null; }
    }

    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionHand;values()[Lnet/minecraft/world/InteractionHand;"), require = 0)
    private InteractionHand[] lads$hands(Operation<InteractionHand[]> original) {
        if (lads$hand != null) return new InteractionHand[] {lads$hand};
        return lads$ready && OffhandKeyModule.active() ? new InteractionHand[] {InteractionHand.MAIN_HAND} : original.call();
    }
}
