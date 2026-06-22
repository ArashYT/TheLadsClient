/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.arguments.ArgumentType
 *  com.mojang.brigadier.arguments.BoolArgumentType
 *  com.mojang.brigadier.arguments.IntegerArgumentType
 *  com.mojang.brigadier.arguments.StringArgumentType
 *  com.mojang.brigadier.builder.ArgumentBuilder
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  com.mojang.brigadier.suggestion.SuggestionsBuilder
 *  net.fabricmc.fabric.api.client.command.v2.ClientCommands
 *  net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
 *  net.minecraft.ChatFormatting
 *  net.minecraft.commands.SharedSuggestionProvider
 *  net.minecraft.network.chat.Component
 */
package com.thelads.core.features.signalloss;

import com.thelads.core.features.signalloss.SignalLossConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class SignalLossCommands {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder root = ClientCommands.literal((String)"signalloss");
        root.then(ClientCommands.literal((String)"reload").executes(context -> {
            SignalLossConfig.load();
            ((FabricClientCommandSource)context.getSource()).sendFeedback((Component)Component.translatable((String)"signalloss.command.reload").withStyle(ChatFormatting.GREEN));
            return 1;
        }));
        LiteralArgumentBuilder config = ClientCommands.literal((String)"config");
        config.then(ClientCommands.literal((String)"reset").executes(context -> {
            SignalLossConfig.INSTANCE.reset();
            SignalLossConfig.save();
            ((FabricClientCommandSource)context.getSource()).sendFeedback((Component)Component.translatable((String)"signalloss.command.reset").withStyle(ChatFormatting.GREEN));
            return 1;
        }));
        config.then(ClientCommands.literal((String)"enabled").then(ClientCommands.argument((String)"value", (ArgumentType)BoolArgumentType.bool()).executes(context -> {
            boolean val;
            SignalLossConfig.INSTANCE.enabled = val = BoolArgumentType.getBool((CommandContext)context, (String)"value");
            SignalLossConfig.save();
            ((FabricClientCommandSource)context.getSource()).sendFeedback((Component)Component.translatable((String)"signalloss.command.set.enabled", (Object[])new Object[]{val}).withStyle(ChatFormatting.YELLOW));
            return 1;
        })));
        config.then(ClientCommands.literal((String)"timeoutThreshold").then(ClientCommands.argument((String)"milliseconds", (ArgumentType)IntegerArgumentType.integer((int)0)).executes(context -> {
            int val;
            SignalLossConfig.INSTANCE.timeoutThreshold = val = IntegerArgumentType.getInteger((CommandContext)context, (String)"milliseconds");
            SignalLossConfig.save();
            ((FabricClientCommandSource)context.getSource()).sendFeedback((Component)Component.translatable((String)"signalloss.command.set.timeout", (Object[])new Object[]{val}).withStyle(ChatFormatting.YELLOW));
            return 1;
        })));
        config.then(ClientCommands.literal((String)"minWarningTime").then(ClientCommands.argument((String)"milliseconds", (ArgumentType)IntegerArgumentType.integer((int)0)).executes(context -> {
            int val;
            SignalLossConfig.INSTANCE.minWarningTime = val = IntegerArgumentType.getInteger((CommandContext)context, (String)"milliseconds");
            SignalLossConfig.save();
            ((FabricClientCommandSource)context.getSource()).sendFeedback((Component)Component.translatable((String)"signalloss.command.set.min_warning", (Object[])new Object[]{val}).withStyle(ChatFormatting.YELLOW));
            return 1;
        })));
        config.then(ClientCommands.literal((String)"lingerTime").then(ClientCommands.argument((String)"milliseconds", (ArgumentType)IntegerArgumentType.integer((int)0)).executes(context -> {
            int val;
            SignalLossConfig.INSTANCE.lingerTime = val = IntegerArgumentType.getInteger((CommandContext)context, (String)"milliseconds");
            SignalLossConfig.save();
            ((FabricClientCommandSource)context.getSource()).sendFeedback((Component)Component.translatable((String)"signalloss.command.set.linger", (Object[])new Object[]{val}).withStyle(ChatFormatting.YELLOW));
            return 1;
        })));
        config.then(ClientCommands.literal((String)"drawBackground").then(ClientCommands.argument((String)"visible", (ArgumentType)BoolArgumentType.bool()).executes(context -> {
            boolean val;
            SignalLossConfig.INSTANCE.drawBackground = val = BoolArgumentType.getBool((CommandContext)context, (String)"visible");
            SignalLossConfig.save();
            ((FabricClientCommandSource)context.getSource()).sendFeedback((Component)Component.translatable((String)"signalloss.command.set.background", (Object[])new Object[]{val}).withStyle(ChatFormatting.YELLOW));
            return 1;
        })));
        config.then(ClientCommands.literal((String)"textColor").then(ClientCommands.argument((String)"hex", (ArgumentType)StringArgumentType.word()).executes(context -> {
            String hex = StringArgumentType.getString((CommandContext)context, (String)"hex");
            try {
                int color;
                SignalLossConfig.INSTANCE.textColor = color = SignalLossCommands.parseColor(hex);
                SignalLossConfig.save();
                ((FabricClientCommandSource)context.getSource()).sendFeedback((Component)Component.translatable((String)"signalloss.command.set.textcolor", (Object[])new Object[]{String.format("#%08X", color)}).withStyle(ChatFormatting.YELLOW));
                return 1;
            }
            catch (NumberFormatException e) {
                ((FabricClientCommandSource)context.getSource()).sendError((Component)Component.translatable((String)"signalloss.command.error.color"));
                return 0;
            }
        })));
        config.then(ClientCommands.literal((String)"backgroundColor").then(ClientCommands.argument((String)"hex", (ArgumentType)StringArgumentType.word()).executes(context -> {
            String hex = StringArgumentType.getString((CommandContext)context, (String)"hex");
            try {
                int color;
                SignalLossConfig.INSTANCE.backgroundColor = color = SignalLossCommands.parseColor(hex);
                SignalLossConfig.save();
                ((FabricClientCommandSource)context.getSource()).sendFeedback((Component)Component.translatable((String)"signalloss.command.set.bgcolor", (Object[])new Object[]{String.format("#%08X", color)}).withStyle(ChatFormatting.YELLOW));
                return 1;
            }
            catch (NumberFormatException e) {
                ((FabricClientCommandSource)context.getSource()).sendError((Component)Component.translatable((String)"signalloss.command.error.color"));
                return 0;
            }
        })));
        config.then(ClientCommands.literal((String)"showInSingleplayer").then(ClientCommands.argument((String)"enabled", (ArgumentType)BoolArgumentType.bool()).executes(context -> {
            boolean val;
            SignalLossConfig.INSTANCE.showInSingleplayer = val = BoolArgumentType.getBool((CommandContext)context, (String)"enabled");
            SignalLossConfig.save();
            ((FabricClientCommandSource)context.getSource()).sendFeedback((Component)Component.translatable((String)"signalloss.command.set.singleplayer", (Object[])new Object[]{val}).withStyle(ChatFormatting.YELLOW));
            return 1;
        })));
        config.then(ClientCommands.literal((String)"position").then(ClientCommands.argument((String)"pos", (ArgumentType)StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest((String[])new String[]{"LEFT", "CENTER", "RIGHT"}, (SuggestionsBuilder)builder)).executes(context -> {
            String input = StringArgumentType.getString((CommandContext)context, (String)"pos").toUpperCase();
            try {
                SignalLossConfig.ToastPosition newPos;
                SignalLossConfig.INSTANCE.toastPosition = newPos = SignalLossConfig.ToastPosition.valueOf(input);
                SignalLossConfig.save();
                ((FabricClientCommandSource)context.getSource()).sendFeedback((Component)Component.translatable((String)"signalloss.command.set.position", (Object[])new Object[]{newPos.name()}).withStyle(ChatFormatting.YELLOW));
                return 1;
            }
            catch (IllegalArgumentException e) {
                ((FabricClientCommandSource)context.getSource()).sendError((Component)Component.translatable((String)"signalloss.command.error.position"));
                return 0;
            }
        })));
        root.then((ArgumentBuilder)config);
        dispatcher.register(root);
    }

    private static int parseColor(String input) throws NumberFormatException {
        if (input.startsWith("#")) {
            input = input.substring(1);
        } else if (input.startsWith("0x")) {
            input = input.substring(2);
        }
        if (input.length() != 6 && input.length() != 8) {
            throw new NumberFormatException("Invalid hex length");
        }
        long colorVal = Long.parseLong(input, 16);
        if (input.length() == 6) {
            colorVal |= 0xFF000000L;
        }
        return (int)colorVal;
    }
}

