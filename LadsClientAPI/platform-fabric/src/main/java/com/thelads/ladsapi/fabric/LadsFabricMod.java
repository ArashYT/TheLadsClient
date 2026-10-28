package com.thelads.ladsapi.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class LadsFabricMod implements ModInitializer {
    private static final Logger LOGGER = Logger.getLogger("LadsClientAPI");
    private static LadsFabricPlatform platform;

    @Override
    public void onInitialize() {
        platform = new LadsFabricPlatform();
        LadsCore core = LadsCore.start(platform);
        if (core == null) {
            LOGGER.info("[LadsClientAPI] LadsCore already started by another platform; Fabric entrypoint inactive.");
            return;
        }

        // Register payload codec (max 1 MiB) in play S2C + C2S registries
        PayloadTypeRegistry.clientboundPlay().registerLarge(LadsPayload.TYPE, LadsPayload.CODEC, 1024 * 1024);
        PayloadTypeRegistry.serverboundPlay().registerLarge(LadsPayload.TYPE, LadsPayload.CODEC, 1024 * 1024);

        // C2S global receiver -> LadsCore.receive
        ServerPlayNetworking.registerGlobalReceiver(LadsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            LadsPlayerHandle handle = platform.getOrCreatePlayer(player);
            LadsCore currentCore = LadsCore.get();
            if (currentCore != null) {
                currentCore.receive(handle, payload.data());
            }
        });

        // Connection events: JOIN / DISCONNECT
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            platform.setServer(server);
            ServerPlayer player = handler.player;
            LadsPlayerHandle handle = platform.getOrCreatePlayer(player);
            LadsCore currentCore = LadsCore.get();
            if (currentCore != null) {
                currentCore.join(handle);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            UUID uuid = player.getUUID();
            platform.removePlayer(uuid);
            LadsCore currentCore = LadsCore.get();
            if (currentCore != null) {
                currentCore.quit(uuid);
            }
        });

        // World change: ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
            LadsPlayerHandle handle = platform.getOrCreatePlayer(player);
            LadsCore currentCore = LadsCore.get();
            if (currentCore != null) {
                currentCore.worldChanged(handle);
            }
        });

        // Respawn (death, End exit) builds a new ServerPlayer: the session must follow it, and a respawn into another world needs a new policy
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            LadsPlayerHandle handle = platform.getOrCreatePlayer(newPlayer);
            LadsCore currentCore = LadsCore.get();
            if (currentCore != null) {
                currentCore.respawned(handle);
            }
        });

        // /ladsapi via CommandRegistrationCallback (admin = permission level 2)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                Commands.literal("ladsapi")
                    .requires(source -> FabricPermissions.check(source, "ladsapi.admin", 2))
                    .executes(context -> {
                        executeCommand(context.getSource(), new String[0]);
                        return 1;
                    })
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(context -> {
                            String rawArgs = StringArgumentType.getString(context, "args");
                            String[] split = rawArgs.trim().isEmpty() ? new String[0] : rawArgs.trim().split("\\s+");
                            executeCommand(context.getSource(), split);
                            return 1;
                        })
                    )
            );
        });

        LOGGER.info("[LadsClientAPI] Fabric platform initialized.");
    }

    private static void executeCommand(CommandSourceStack source, String[] args) {
        boolean admin = FabricPermissions.check(source, "ladsapi.admin", 2);
        LadsCore core = LadsCore.get();
        if (core != null) {
            List<String> output = core.command(args, admin);
            for (String line : output) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
    }
}
