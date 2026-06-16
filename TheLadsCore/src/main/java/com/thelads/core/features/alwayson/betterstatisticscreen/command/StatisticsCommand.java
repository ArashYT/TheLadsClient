package com.thelads.core.features.alwayson.betterstatisticscreen.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.thelads.core.features.alwayson.betterstatisticscreen.BetterStats;
import com.thelads.core.mixin.alwayson.betterstatisticscreen.AccessorStatsCounter;
import com.thelads.core.features.alwayson.betterstatisticscreen.resource.BLanguage;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StatisticsCommand {
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_STAT = (context, builder) -> {
        @Nullable StatType<?> statType = null;
        try {
            statType = ResourceArgument.getResource(context, "stat_type", Registries.STAT_TYPE).value();
        }
        catch (Exception exception) {
            // empty catch block
        }
        if (statType == null) {
            return IdentifierArgument.id().listSuggestions(context, builder);
        }
        @Nullable List<Identifier> suggestions = statType.getRegistry().registryKeySet().stream().map(ResourceKey::identifier).toList();
        return SharedSuggestionProvider.suggest(StreamSupport.stream(suggestions.spliterator(), false).map(Objects::toString), builder);
    };

    private StatisticsCommand() {
    }

    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher, @NotNull CommandBuildContext buildContext) throws NullPointerException {
        LiteralArgumentBuilder<CommandSourceStack> statistics = Commands.literal("statistics")
                .requires(scs -> scs.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(StatisticsCommand.statistics_edit(buildContext))
                .then(StatisticsCommand.statistics_clear())
                .then(StatisticsCommand.statistics_query(buildContext));
        LiteralArgumentBuilder<CommandSourceStack> stats = Commands.literal("stats")
                .requires(scs -> scs.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(StatisticsCommand.statistics_edit(buildContext))
                .then(StatisticsCommand.statistics_clear())
                .then(StatisticsCommand.statistics_query(buildContext));
        dispatcher.register(statistics);
        dispatcher.register(stats);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> statistics_edit(@NotNull CommandBuildContext cbc) throws NullPointerException {
        return Commands.literal("edit")
                .then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("stat_type", ResourceArgument.resource(cbc, Registries.STAT_TYPE))
                .then(Commands.argument("stat", IdentifierArgument.id())
                .suggests(SUGGEST_STAT)
                .then(Commands.literal("set")
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                .executes(ctx -> StatisticsCommand.execute_edit(ctx, true))))
                .then(Commands.literal("increase")
                .then(Commands.argument("value", IntegerArgumentType.integer())
                .executes(ctx -> StatisticsCommand.execute_edit(ctx, false)))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> statistics_clear() {
        return Commands.literal("clear")
                .then(Commands.argument("targets", EntityArgument.players())
                .executes(StatisticsCommand::execute_clear));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> statistics_query(@NotNull CommandBuildContext cra) throws NullPointerException {
        return Commands.literal("query")
                .then(Commands.argument("target", EntityArgument.player())
                .then(Commands.argument("stat_type", ResourceArgument.resource(cra, Registries.STAT_TYPE))
                .then(Commands.argument("stat", IdentifierArgument.id())
                .suggests(SUGGEST_STAT)
                .executes(StatisticsCommand::execute_query))));
    }

    @SuppressWarnings("unchecked")
    private static int execute_edit(@NotNull CommandContext<CommandSourceStack> context, boolean setOrIncrease) throws NullPointerException {
        try {
            Collection<ServerPlayer> arg_targets = EntityArgument.getPlayers(context, "targets");
            StatType<Object> arg_stat_type = (StatType<Object>) ResourceArgument.getResource(context, "stat_type", Registries.STAT_TYPE).value();
            Identifier arg_stat = IdentifierArgument.getId(context, "stat");
            int arg_value = IntegerArgumentType.getInteger(context, "value");
            Object stat_object = arg_stat_type.getRegistry().getOptional(arg_stat).orElse(null);
            Objects.requireNonNull(stat_object, "Registry entry '" + arg_stat + "' does not exist for registry '" + arg_stat_type.getRegistry() + "'.");
            Stat<Object> stat = arg_stat_type.get(stat_object);
            AtomicInteger affected = new AtomicInteger();
            for (ServerPlayer target : arg_targets) {
                if (target == null) continue;
                if (setOrIncrease) {
                    target.getStats().setValue(target, stat, arg_value);
                } else {
                    target.getStats().increment(target, stat, arg_value);
                }
                affected.incrementAndGet();
                target.getStats().sendStats(target);
            }
            context.getSource().sendSuccess(() -> BLanguage.cmd_stats_edit_out(Component.literal("[" + BuiltInRegistries.STAT_TYPE.getKey(arg_stat_type) + " / " + arg_stat + "]"), affected.get()), false);
            return affected.get();
        }
        catch (Exception e) {
            StatisticsCommand.handleError(context, e);
            return -1;
        }
    }

    private static int execute_clear(@NotNull CommandContext<CommandSourceStack> context) throws NullPointerException {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
            AtomicInteger affected = new AtomicInteger();
            for (ServerPlayer target : targets) {
                if (target == null) continue;
                ((AccessorStatsCounter)target.getStats()).getStats().clear();
                affected.incrementAndGet();
                target.connection.disconnect(Component.literal("").append(BLanguage.cmd_stats_clear_kick()).append("\n\n[EN]: Your statistics were cleared, which requires you to disconnect and re-join."));
            }
            context.getSource().sendSuccess(() -> BLanguage.cmd_stats_clear_out(affected.get()), false);
            return affected.get();
        }
        catch (Exception e) {
            StatisticsCommand.handleError(context, e);
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private static int execute_query(@NotNull CommandContext<CommandSourceStack> context) throws NullPointerException {
        try {
            ServerPlayer arg_target = EntityArgument.getPlayer(context, "target");
            if (arg_target == null) {
                throw new SimpleCommandExceptionType(Component.literal("Player not found.")).create();
            }
            StatType<Object> arg_stat_type = (StatType<Object>) ResourceArgument.getResource(context, "stat_type", Registries.STAT_TYPE).value();
            Identifier arg_stat = IdentifierArgument.getId(context, "stat");
            Object stat_object = arg_stat_type.getRegistry().getOptional(arg_stat).orElse(null);
            Objects.requireNonNull(stat_object, "Registry entry '" + arg_stat + "' does not exist for registry '" + arg_stat_type.getRegistry() + "'.");
            Stat<Object> stat = arg_stat_type.get(stat_object);
            int statValue = arg_target.getStats().getValue(stat);
            context.getSource().sendSuccess(() -> BLanguage.cmd_stats_query_out(arg_target.getDisplayName(), Component.literal("[" + BuiltInRegistries.STAT_TYPE.getKey(arg_stat_type) + " / " + arg_stat + "]"), statValue), false);
            return statValue;
        }
        catch (Exception e) {
            StatisticsCommand.handleError(context, e);
            return -1;
        }
    }

    @ApiStatus.Internal
    public static void handleError(@NotNull CommandContext<CommandSourceStack> context, @NotNull Throwable throwable) throws NullPointerException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(throwable);
        if (throwable instanceof Error) {
            throw new Error("An unexpected error occurred trying to execute the /statistics command", throwable);
        }
        context.getSource().sendFailure(Component.translatable("command.failed").append(":\n    " + throwable.getMessage()));
        BetterStats.LOGGER.error("An unexpected error occurred trying to execute the /statistics command", throwable);
    }
}
