package com.thelads.core.v1_20_1.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.hypixel.HypixelMods;
import com.thelads.core.v1_20_1.feature.HypixelTabFill1201;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow @Final EditBox input;

    @Inject(method = "updateCommandInfo()V", at = @At("RETURN"))
    private void ladsHypixelSuggestions(CallbackInfo ci) {
        if (!HypixelSafetyManager.isHypixel() || !HypixelMods.isTabFillEnabled()) {
            return;
        }
        if (input == null) return;
        String text = input.getValue();
        if (text == null || !text.startsWith("/")) return;
        int cursor = input.getCursorPosition();

        if (pendingSuggestions == null) {
            pendingSuggestions = CompletableFuture.completedFuture(
                HypixelTabFill1201.createSuggestions(text, cursor)
            );
        } else {
            pendingSuggestions = pendingSuggestions.thenApply(
                serverSugg -> HypixelTabFill1201.mergeSuggestions(serverSugg, text, cursor)
            );
        }
    }
}
