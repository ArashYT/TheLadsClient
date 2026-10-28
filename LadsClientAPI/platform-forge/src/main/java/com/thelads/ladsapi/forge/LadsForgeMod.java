package com.thelads.ladsapi.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

@Mod("ladsclientapi")
public class LadsForgeMod {
    public static final String MODID = "ladsclientapi";
    public static final ResourceLocation CHANNEL_NAME = new ResourceLocation("lads", "api");
    private static final Logger LOGGER = Logger.getLogger("LadsClientAPI");

    private static EventNetworkChannel channel;
    private static LadsForgePlatform platform;

    public LadsForgeMod() {
        // Safe DisplayTest: server-only mod, clients that load it accidentally must not be broken
        ModLoadingContext.get().registerExtensionPoint(
            IExtensionPoint.DisplayTest.class,
            () -> new IExtensionPoint.DisplayTest(
                () -> NetworkConstants.IGNORESERVERONLY,
                (remoteVersion, isNetwork) -> true
            )
        );

        platform = new LadsForgePlatform();
        LadsCore core = LadsCore.start(platform);
        if (core == null) {
            LOGGER.info("[LadsClientAPI] LadsCore already started by another platform; Forge entrypoint inactive.");
            return;
        }

        // An EventNetworkChannel for lads:api built so clients WITHOUT it still join (accept missing/vanilla)
        channel = NetworkRegistry.newEventChannel(
            CHANNEL_NAME,
            () -> String.valueOf(LadsCore.PROTOCOL),
            NetworkRegistry.acceptMissingOr(String.valueOf(LadsCore.PROTOCOL)),
            NetworkRegistry.acceptMissingOr(String.valueOf(LadsCore.PROTOCOL))
        );

        // receive -> LadsCore.receive
        channel.addListener((NetworkEvent.ServerCustomPayloadEvent event) -> {
            FriendlyByteBuf buf = event.getPayload();
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            NetworkEvent.Context ctx = event.getSource().get();
            ServerPlayer player = ctx.getSender();
            ctx.setPacketHandled(true);
            if (player != null) {
                LadsPlayerHandle handle = platform.getOrCreatePlayer(player);
                LadsCore currentCore = LadsCore.get();
                if (currentCore != null) {
                    currentCore.receive(handle, bytes);
                }
            }
        });

        // Register event listener on MinecraftForge.EVENT_BUS
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("[LadsClientAPI] Forge platform initialized.");
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LadsPlayerHandle handle = platform.getOrCreatePlayer(player);
            LadsCore currentCore = LadsCore.get();
            if (currentCore != null) {
                currentCore.join(handle);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = ForgeReflection.getPlayerUUID(player);
            platform.removePlayer(uuid);
            LadsCore currentCore = LadsCore.get();
            if (currentCore != null) {
                currentCore.quit(uuid);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LadsPlayerHandle handle = platform.getOrCreatePlayer(player);
            LadsCore currentCore = LadsCore.get();
            if (currentCore != null) {
                currentCore.worldChanged(handle);
            }
        }
    }

    // Respawn (death, End exit) builds a new ServerPlayer: the session must follow it, and a respawn into another world needs a new policy
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LadsPlayerHandle handle = platform.getOrCreatePlayer(player);
            LadsCore currentCore = LadsCore.get();
            if (currentCore != null) {
                currentCore.respawned(handle);
            }
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("ladsapi")
                .requires(source -> ForgeReflection.hasPermission(source, 2))
                .executes(ctx -> {
                    executeCommand(ctx.getSource(), new String[0]);
                    return 1;
                })
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String raw = StringArgumentType.getString(ctx, "args");
                        String[] split = raw.trim().isEmpty() ? new String[0] : raw.trim().split("\\s+");
                        executeCommand(ctx.getSource(), split);
                        return 1;
                    })
                )
        );
    }

    private static void executeCommand(CommandSourceStack source, String[] args) {
        boolean admin = ForgeReflection.hasPermission(source, 2);
        LadsCore core = LadsCore.get();
        if (core != null) {
            List<String> output = core.command(args, admin);
            for (String line : output) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
    }
}
