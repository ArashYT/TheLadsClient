package com.thelads.ladsapi.forge;

import com.thelads.ladsapi.platform.LadsPlayerHandle;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ICustomPacket;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import org.apache.commons.lang3.tuple.Pair;

public class LadsForgePlayerHandle implements LadsPlayerHandle {
    private final ServerPlayer player;
    private final UUID uuid;
    private final String name;

    public LadsForgePlayerHandle(ServerPlayer player) {
        this.player = player;
        this.uuid = ForgeReflection.getPlayerUUID(player);
        this.name = ForgeReflection.getPlayerName(player);
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    @Override
    public UUID uuid() {
        return uuid != null ? uuid : player.getUUID();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String world() {
        return ForgeReflection.getWorldName(player);
    }

    @Override
    public boolean hasPermission(String node) {
        // Op level 2 for admin
        return ForgeReflection.hasPermissions(player, 2);
    }

    @Override
    public void send(byte[] payload) {
        // Send the vanilla clientbound custom payload packet
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
        ICustomPacket<?> customPacket = NetworkDirection.PLAY_TO_CLIENT.buildPacket(Pair.of(buf, 0), LadsForgeMod.CHANNEL_NAME);
        PacketDistributor.PLAYER.with(() -> player).send(customPacket.getThis());
    }

    @Override
    public void kick(String message) {
        ForgeReflection.disconnect(player, message);
    }
}
