// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.mixin.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.SuggestionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(CommandDispatcher.class)
public class CommandDispatcherMixin
{
	@Inject(method = "getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;", at = @At(value = "HEAD"), remap = false)
	private void atGetCompletionSuggestions(ParseResults<?> parse, int cursor, CallbackInfoReturnable<CompletableFuture<Suggestions>> cir)
	{
		SuggestionManager.clearProvided();
	}
}
