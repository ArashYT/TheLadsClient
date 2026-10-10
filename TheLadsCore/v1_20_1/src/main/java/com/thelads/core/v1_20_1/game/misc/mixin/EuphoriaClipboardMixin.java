package com.thelads.core.v1_20_1.game.misc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Euphoria Patches (a Complementary add-on in the pack) copies a download link to the player's clipboard on every launch while
 * the exact Complementary version it patches is not installed, replacing whatever the player had copied. It has no setting for
 * that, so its clipboard queue ignores the link; the message in its log and its in-game notice stay. Absent from a game without
 * the mod: require 0.
 */
@Pseudo
@Mixin(targets = "com.euphoriapatches.euphoria_patcher.util.mod.ClipboardManager", remap = false)
public abstract class EuphoriaClipboardMixin {
    @Inject(method = "schedule(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void lads$keepClipboard(String url, CallbackInfo callback) {
        callback.cancel();
    }
}
