// SPDX-License-Identifier: Apache-2.0
// Command names and semantics adapted from SignalLoss 1.2.1+26.2, Copyright Hexandcube.
package com.thelads.core.v26_2.feature;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.TextOption;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

final class NativeSignalLossCommands {
    private NativeSignalLossCommands() {}
    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var module = NativeConnectionStatus.module();
        var root = literal("signalloss");
        root.then(literal("reload").executes(context -> {
            try { NativeConnectionStatus.reloadPreferences(); context.getSource().sendFeedback(Component.literal("Lads SignalLoss preferences reloaded.")); return 1; }
            catch (Exception failed) { context.getSource().sendError(Component.literal("SignalLoss preferences could not be reloaded: " + failed.getMessage())); return 0; }
        }));
        var config = literal("config");
        config.then(literal("reset").executes(context -> { NativeConnectionStatus.resetPreferences(); context.getSource().sendFeedback(Component.literal("Lads SignalLoss preferences reset.")); return 1; }));
        config.then(bool("enabled", module::setEnabled));
        config.then(bool("drawBackground", module.background::set));
        config.then(bool("showInSingleplayer", module.singleplayer::set));
        config.then(timing("timeoutThreshold", module.timeout));
        config.then(timing("minWarningTime", module.minimum));
        config.then(timing("lingerTime", module.linger));
        config.then(color("textColor", module.textColor));
        config.then(color("backgroundColor", module.backgroundColor));
        config.then(literal("position").then(argument("pos", StringArgumentType.word())
            .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"LEFT", "CENTER", "RIGHT"}, builder))
            .executes(context -> {
                String value = StringArgumentType.getString(context, "pos").toUpperCase(Locale.ROOT);
                int index = switch (value) { case "LEFT" -> 0; case "CENTER" -> 1; case "RIGHT" -> 2; default -> -1; };
                if (index < 0) { context.getSource().sendError(Component.literal("Use LEFT, CENTER or RIGHT.")); return 0; }
                module.position.setIndex(index); saved(context.getSource(), "position", value); return 1;
            })));
        dispatcher.register(root.then(config));
    }
    private static LiteralArgumentBuilder<FabricClientCommandSource> bool(String name, Consumer<Boolean> setter) {
        return literal(name).then(argument("value", BoolArgumentType.bool()).executes(context -> {
            boolean value = BoolArgumentType.getBool(context, "value"); setter.accept(value); saved(context.getSource(), name, value); return 1;
        }));
    }
    private static LiteralArgumentBuilder<FabricClientCommandSource> timing(String name, TextOption option) {
        return literal(name).then(argument("milliseconds", IntegerArgumentType.integer(0)).executes(context -> {
            int value = IntegerArgumentType.getInteger(context, "milliseconds"); option.setValue(Integer.toString(value)); saved(context.getSource(), name, value); return 1;
        }));
    }
    private static LiteralArgumentBuilder<FabricClientCommandSource> color(String name, ColorOption option) {
        return literal(name).then(argument("hex", StringArgumentType.greedyString()).executes(context -> {
            try {
                int value = parseColor(StringArgumentType.getString(context, "hex")); option.setUseGlobal(false); option.setColor(value);
                saved(context.getSource(), name, String.format(Locale.ROOT, "#%08X", value)); return 1;
            } catch (IllegalArgumentException invalid) { context.getSource().sendError(Component.literal("Use RGB or ARGB hex, for example #FF5555 or A0000000.")); return 0; }
        }));
    }
    static int parseColor(String text) {
        String value = text.trim();
        if (value.startsWith("#")) value = value.substring(1);
        else if (value.startsWith("0x") || value.startsWith("0X")) value = value.substring(2);
        if (!value.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new IllegalArgumentException("Invalid color");
        return (int) (Long.parseLong(value, 16) | (value.length() == 6 ? 0xff000000L : 0));
    }
    private static void saved(FabricClientCommandSource source, String name, Object value) {
        ConfigManager.save(); source.sendFeedback(Component.literal("SignalLoss " + name + ": " + value));
    }
}
