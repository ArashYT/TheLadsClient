// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.mixin.suggestions.arguments;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.arguments.StyleArgument;
import net.minecraft.network.chat.Style;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.SuggestionManager;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.parser.CustomTagParser;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.compound.CompoundType;
import org.spongepowered.asm.mixin.Mixin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Mixin(StyleArgument.class)
public abstract class StyleArgumentMixin implements ArgumentType<Style>
{
	@Override public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> ctx, SuggestionsBuilder builder)
	{
		CustomTagParser parser = CustomTagParser.forJson(builder.getRemaining(), CompoundType.fromName("text/nbtac:style"));
		return SuggestionManager.finishSuggestions(parser::parse, builder, Function.identity());
	}
}
