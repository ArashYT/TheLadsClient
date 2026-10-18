package com.thelads.smp.features.arise.nb1;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Official Simple Voice Chat plugin implementing server voice broadcasting.
 * Routes voice audio globally across all connected players when speaking in the broadcast group.
 */
public class SimpleVoiceChatBroadcastPlugin implements VoicechatPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-VoiceBroadcast");

    @Override
    public String getPluginId() {
        return "ladssmp_voicechat_broadcast";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
        LOGGER.info("[Lads SMP] Simple Voice Chat Broadcast plugin registered");
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        if (!AriseNB1Config.get().voicechatBroadcast.enabled) {
            return;
        }

        VoicechatConnection senderConn = event.getSenderConnection();
        if (senderConn == null) return;

        ServerPlayer sender = senderConn.getPlayer();
        if (sender == null) return;

        Object rawPlayer = sender.getPlayer();
        if (!(rawPlayer instanceof net.minecraft.server.level.ServerPlayer mcSender)) {
            return;
        }

        int reqLevel = AriseNB1Config.get().voicechatBroadcast.permissionLevel;
        net.minecraft.server.permissions.Permission perm = switch (reqLevel) {
            case 1 -> net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR;
            case 3 -> net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN;
            case 4 -> net.minecraft.server.permissions.Permissions.COMMANDS_OWNER;
            default -> net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER;
        };
        if (!mcSender.permissions().hasPermission(perm)) {
            return;
        }

        Group group = senderConn.getGroup();
        if (group == null) return;

        String broadcastGroup = AriseNB1Config.get().voicechatBroadcast.broadcastGroupName;
        if (!broadcastGroup.equalsIgnoreCase(group.getName().strip())) {
            return;
        }

        // Cancel the normal locational / proximity audio packet
        event.cancel();

        VoicechatServerApi api = event.getVoicechat();
        if (api == null) return;

        MinecraftServer server = mcSender.level().getServer();
        if (server == null) return;

        StaticSoundPacket staticPacket = event.getPacket().toStaticSoundPacket();

        for (net.minecraft.server.level.ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (recipient.equals(mcSender)) continue;
            VoicechatConnection targetConn = api.getConnectionOf(recipient.getUUID());
            if (targetConn != null) {
                api.sendStaticSoundPacketTo(targetConn, staticPacket);
            }
        }
    }
}
