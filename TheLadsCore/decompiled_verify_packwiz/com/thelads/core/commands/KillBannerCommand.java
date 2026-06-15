/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  net.fabricmc.fabric.api.client.command.v2.ClientCommands
 *  net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
 */
package com.thelads.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.thelads.core.modules.KillBannerModule;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class KillBannerCommand {
    private final KillBannerModule killBannerModule;

    public KillBannerCommand(KillBannerModule killBannerModule) {
        this.killBannerModule = killBannerModule;
    }

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal((String)"testkillbanner").executes(context -> {
            this.killBannerModule.handleTestKillBannerCommand();
            return 1;
        }));
    }
}

