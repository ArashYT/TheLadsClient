package com.thelads.core.v1_8_9.hudextras.mixin;

import com.thelads.core.client.hud.HudExtras;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla HUD's held item name (kept, or gone without its fade) and Title Tweaker's Send to Chat and Hide on Screen. */
@Mixin(GuiIngame.class)
public abstract class GuiIngameTweaksMixin189 {
    @Shadow protected int remainingHighlightTicks;
    @Shadow protected ItemStack highlightingItemStack;

    @Inject(method = "updateTick", at = @At("TAIL"))
    private void lads$itemName(CallbackInfo ci) {
        remainingHighlightTicks = HudExtras.VANILLA.highlightTicks(remainingHighlightTicks, highlightingItemStack != null);
    }

    /** The server's title packet (title, subtitle or times; a reset sends empty texts). */
    @Inject(method = "displayTitle", at = @At("HEAD"), cancellable = true)
    private void lads$title(String title, String subtitle, int fadeIn, int stay, int fadeOut, CallbackInfo ci) {
        boolean hide = title != null && !title.isEmpty() && HudExtras.title(title, false);
        hide |= subtitle != null && !subtitle.isEmpty() && HudExtras.title(subtitle, true);
        if (hide) ci.cancel();
    }
}
