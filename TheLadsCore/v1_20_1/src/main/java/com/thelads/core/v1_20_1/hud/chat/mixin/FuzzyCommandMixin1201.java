package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.thelads.core.client.ChatTweaks;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fuzzy Command Suggestions (Chat module, ChatTweaks.fuzzyMatch): a command word in the tree the server sent also comes up when the
 * typed letters fit it loosely ("/gmd" suggests "gamemode"). Suggestions only: nothing is sent until the player sends the line.
 */
@Mixin(value = LiteralCommandNode.class, remap = false)
public abstract class FuzzyCommandMixin1201 {
    @Shadow(remap = false) @Final private String literal;
    @Shadow(remap = false) @Final private String literalLowerCase;

    @Inject(method = "listSuggestions", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void lads$fuzzy(CommandContext<?> context, SuggestionsBuilder builder, CallbackInfoReturnable<CompletableFuture<Suggestions>> cir) {
        if (ChatTweaks.fuzzy && ChatTweaks.fuzzyMatch(literalLowerCase, builder.getRemainingLowerCase())) cir.setReturnValue(builder.suggest(literal).buildFuture());
    }
}
