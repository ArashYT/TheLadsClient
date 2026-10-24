package com.thelads.ladsapi.fabric;

import com.thelads.ladsapi.platform.LadsPlayerHandle;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class LadsFabricPlayerHandle implements LadsPlayerHandle {
    private final ServerPlayer player;

    public LadsFabricPlayerHandle(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    @Override
    public UUID uuid() {
        return player.getUUID();
    }

    @Override
    public String name() {
        return player.getGameProfile().name();
    }

    @Override
    public String world() {
        return player.level().dimension().identifier().toString();
    }

    @Override
    public boolean hasPermission(String node) {
        return FabricPermissions.check(player, node, 2);
    }

    @Override
    public void send(byte[] payload) {
        // Send WITHOUT the canSend gate (the client never announces the channel - PROTOCOL 1)
        ServerPlayNetworking.send(player, new LadsPayload(payload));
    }

    @Override
    public void kick(String message) {
        player.connection.disconnect(Component.literal(message));
    }
}
