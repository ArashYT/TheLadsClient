/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  net.minecraft.ChatFormatting
 *  net.minecraft.commands.CommandBuildContext
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.commands.Commands
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.server.permissions.PermissionCheck
 */
package com.thelads.core.features.alwayson.clientsort.command;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.thelads.core.features.alwayson.clientsort.config.ServerConfig;
import com.thelads.core.features.alwayson.clientsort.util.Localization;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.permissions.PermissionCheck;

public class ModCommands<S>
extends CommandDispatcher<S> {
    public void register(CommandDispatcher<S> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"clientsort").requires((Predicate)Commands.hasPermission((PermissionCheck)Commands.LEVEL_GAMEMASTERS))).then(Commands.literal((String)"reload").executes(ctx -> {
            MutableComponent msg = Component.empty().append((Component)Component.literal((String)"[").withStyle(ChatFormatting.DARK_GRAY)).append((Component)Component.literal((String)"Client").withStyle(ChatFormatting.AQUA)).append((Component)Component.literal((String)"Sort").withStyle(ChatFormatting.DARK_AQUA)).append((Component)Component.literal((String)"] ").withStyle(ChatFormatting.DARK_GRAY)).withStyle(ChatFormatting.GRAY);
            ServerConfig.reloadAndSave();
            msg.append((Component)Localization.localized("message", "configReloaded", Component.literal((String)"clientsort-server.json").withStyle(ChatFormatting.GOLD)));
            ((CommandSourceStack)ctx.getSource()).sendSystemMessage((Component)msg);
            return 1;
        })));
    }
}
