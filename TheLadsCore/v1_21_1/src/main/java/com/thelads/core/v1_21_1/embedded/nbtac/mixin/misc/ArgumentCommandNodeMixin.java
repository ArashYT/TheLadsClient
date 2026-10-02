// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.mixin.misc;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.arguments.EntityArgument;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ArgumentCommandNode.class)
public class ArgumentCommandNodeMixin
{
	@Shadow(remap = false) @Final private SuggestionProvider<?> customSuggestions;
	@Shadow(remap = false) @Final private ArgumentType<?> type;

	// Fix for conflict with Paper servers
	@Inject(method = "listSuggestions", at = @At(value = "HEAD"), cancellable = true, remap = false)
	private void listSuggestions(CommandContext<?> context, SuggestionsBuilder builder,
								 CallbackInfoReturnable<CompletableFuture<Suggestions>> cir)
	{
		if (customSuggestions != null && builder.getRemaining().contains("{") && type instanceof EntityArgument)
		{
			cir.setReturnValue(type.listSuggestions(context, builder));
			cir.cancel();
		}
	}
}
