package com.thelads.core.features.auto.modernadvancements.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancements;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.data.api.FeedEvent;
import com.thelads.core.features.auto.modernadvancements.server.ServerPlayerAdvancementTracker;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public class ModernAdvancementsNetworking {
   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay()
         .register(ModernAdvancementsPackets.ClientHandshakePacket.PACKET_TYPE, ModernAdvancementsPackets.ClientHandshakePacket.CODEC);
      PayloadTypeRegistry.serverboundPlay()
         .register(ModernAdvancementsPackets.RequestPlayerDetailPacket.PACKET_TYPE, ModernAdvancementsPackets.RequestPlayerDetailPacket.CODEC);
      PayloadTypeRegistry.clientboundPlay()
         .register(ModernAdvancementsPackets.ServerAdvancementsPacket.PACKET_TYPE, ModernAdvancementsPackets.ServerAdvancementsPacket.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(ModernAdvancementsPackets.PlayerListPacket.PACKET_TYPE, ModernAdvancementsPackets.PlayerListPacket.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(ModernAdvancementsPackets.FeedUpdatePacket.PACKET_TYPE, ModernAdvancementsPackets.FeedUpdatePacket.CODEC);
      PayloadTypeRegistry.clientboundPlay()
         .register(ModernAdvancementsPackets.PlayerDetailPacket.PACKET_TYPE, ModernAdvancementsPackets.PlayerDetailPacket.CODEC);
   }

   public static void registerServerHandlers() {
      ServerPlayNetworking.registerGlobalReceiver(
         ModernAdvancementsPackets.ClientHandshakePacket.PACKET_TYPE,
         (payload, context) -> {
            if (payload.hasMod()) {
               context.server()
                  .execute(
                     () -> {
                        ServerPlayerAdvancementTracker tracker = ServerPlayerAdvancementTracker.getInstance();
                        tracker.registerModPlayer(context.player().getUUID());
                        if (ModernAdvancements.CONFIG.shouldSendAdvancementData()) {
                           List<AdvancementHolder> holders = new ArrayList<>(context.server().getAdvancements().getAllAdvancements());
                           Map<Identifier, ModernAdvancementsPackets.SimpleRewards> rewards = new HashMap<>();

                           for (AdvancementHolder h : holders) {
                              AdvancementRewards r = h.value().rewards();
                              if (r.experience() > 0 || !r.loot().isEmpty() || !r.recipes().isEmpty() || r.function().isPresent()) {
                                 rewards.put(
                                    h.id(),
                                    new ModernAdvancementsPackets.SimpleRewards(
                                       r.experience(),
                                       r.loot().stream().<Identifier>map(ResourceKey::identifier).toList(),
                                       r.recipes().stream().<Identifier>map(ResourceKey::identifier).toList(),
                                       r.function().isPresent()
                                    )
                                 );
                              }
                           }

                           ServerPlayNetworking.send(context.player(), new ModernAdvancementsPackets.ServerAdvancementsPacket(holders, rewards));
                        }

                        tracker.sendInitialData(context.player());
                     }
                  );
            }
         }
      );
      ServerPlayNetworking.registerGlobalReceiver(
         ModernAdvancementsPackets.RequestPlayerDetailPacket.PACKET_TYPE,
         (payload, context) -> context.server()
            .execute(() -> ServerPlayerAdvancementTracker.getInstance().handleDetailRequest(context.player(), payload.targetUuid()))
      );
   }

   public static void registerClientHandlers() {
      ClientPlayNetworking.registerGlobalReceiver(
         ModernAdvancementsPackets.ServerAdvancementsPacket.PACKET_TYPE, (payload, context) -> context.client().execute(() -> {
            AdvancementTree tree = new AdvancementTree();
            tree.addAll(payload.advancements());
            ModernAdvancementsClient.setServerAdvancementTree(tree);
            ModernAdvancementsClient.serverRewards.clear();
            ModernAdvancementsClient.serverRewards.putAll(payload.rewards());
         })
      );
      ClientPlayNetworking.registerGlobalReceiver(
         ModernAdvancementsPackets.PlayerListPacket.PACKET_TYPE,
         (payload, context) -> context.client().execute(() -> ModernAdvancementsClient.serverLeaderboard = payload.players())
      );
      ClientPlayNetworking.registerGlobalReceiver(
         ModernAdvancementsPackets.FeedUpdatePacket.PACKET_TYPE, (payload, context) -> context.client().execute(() -> {
            List<FeedEvent> merged = new ArrayList<>(payload.events());

            for (FeedEvent e : ModernAdvancementsClient.serverFeed) {
               if (!merged.contains(e)) {
                  merged.add(e);
               }
            }

            if (merged.size() > 200) {
               merged = merged.subList(0, 200);
            }

            ModernAdvancementsClient.serverFeed = merged;
         })
      );
      ClientPlayNetworking.registerGlobalReceiver(
         ModernAdvancementsPackets.PlayerDetailPacket.PACKET_TYPE,
         (payload, context) -> context.client().execute(() -> ModernAdvancementsClient.pendingDetailPacket = payload)
      );
   }
}
