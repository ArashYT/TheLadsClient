package com.thelads.core.commands;

import com.thelads.core.modules.KillBannerModule;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class KillBannerCommand {
    private final KillBannerModule killBannerModule;

    public KillBannerCommand(KillBannerModule killBannerModule) {
        this.killBannerModule = killBannerModule;
    }

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("testkillbanner")
            .executes(context -> {
                killBannerModule.handleTestKillBannerCommand(1);
                return 1;
            }));

        dispatcher.register(ClientCommands.literal("testkill")
            .executes(context -> {
                killBannerModule.handleTestKillBannerCommand(1);
                return 1;
            })
            .then(ClientCommands.argument("level", IntegerArgumentType.integer(1, 5))
                .executes(context -> {
                    int level = IntegerArgumentType.getInteger(context, "level");
                    killBannerModule.handleTestKillBannerCommand(level);
                    return 1;
                })));
    }
}