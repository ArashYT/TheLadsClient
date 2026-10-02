// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.mixin.suggestions.arguments;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.types.templates.Tag;
import net.minecraft.commands.arguments.NbtTagArgument;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionManager;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.CustomTagParser;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.Type;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.compound.CompoundType;
import com.thelads.core.v26_2.embedded.nbtac.utils.Utils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Mixin(NbtTagArgument.class)
public abstract class NbtTagArgumentMixin implements ArgumentType<Tag>
{
	@Override public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> ctx, SuggestionsBuilder builder)
	{
		try
		{
			String str = builder.getRemaining();
			Type tagType = getTagType(ctx.getLastChild());
			return tagType != null ? SuggestionManager.get(str, tagType, builder, false, Function.identity()) : Suggestions.empty();
		}
		catch (Exception e)
		{
			return Suggestions.empty();
		}
	}

	@Unique private @Nullable Type getTagType(CommandContext<?> ctx)
	{
		return Utils.getCommandName(ctx).equals("data") ? getTagTypeForDataCommand(ctx) : null;
	}

	@Unique private @Nullable Type getTagTypeForDataCommand(CommandContext<?> ctx)
	{
		String instruction = Utils.getNodeString(ctx, 1);
		if (!instruction.equals("modify")) { return null; }

		String type = Utils.getNodeString(ctx, 2);
		String path = Utils.getArgumentString(ctx, "targetPath");

		String root = switch (type)
		{
			case "block" -> Utils.blockFromCoords(ctx, "targetPos");
			case "entity" -> Utils.entityFromSelector(ctx, "target");
			default -> null;
		};
		if (root == null) { return null; }

		CustomTagParser parser = CustomTagParser.forNbtPath(path, CompoundType.fromName(root));
		parser.parse();
		return parser.pathType;
	}
}
