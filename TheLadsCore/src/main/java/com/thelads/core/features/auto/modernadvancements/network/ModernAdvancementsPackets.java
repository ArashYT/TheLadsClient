package com.thelads.core.features.auto.modernadvancements.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import com.thelads.core.features.auto.modernadvancements.data.api.FeedEvent;
import com.thelads.core.features.auto.modernadvancements.data.api.PlayerSummary;
import com.thelads.core.features.auto.modernadvancements.data.api.TabDetail;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class ModernAdvancementsPackets {
   public record ClientHandshakePacket(boolean hasMod) implements CustomPacketPayload {
      public static final Type<ModernAdvancementsPackets.ClientHandshakePacket> PACKET_TYPE = new Type(
         Identifier.fromNamespaceAndPath("modern-advancements", "client_handshake")
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, ModernAdvancementsPackets.ClientHandshakePacket> CODEC = StreamCodec.ofMember(
         ModernAdvancementsPackets.ClientHandshakePacket::write, ModernAdvancementsPackets.ClientHandshakePacket::read
      );

      private void write(RegistryFriendlyByteBuf buf) {
         buf.writeBoolean(this.hasMod);
      }

      private static ModernAdvancementsPackets.ClientHandshakePacket read(RegistryFriendlyByteBuf buf) {
         return new ModernAdvancementsPackets.ClientHandshakePacket(buf.readBoolean());
      }

      @NotNull
      public Type<? extends CustomPacketPayload> type() {
         return PACKET_TYPE;
      }
   }

   public record FeedUpdatePacket(List<FeedEvent> events) implements CustomPacketPayload {
      public static final Type<ModernAdvancementsPackets.FeedUpdatePacket> PACKET_TYPE = new Type(
         Identifier.fromNamespaceAndPath("modern-advancements", "feed_update")
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, ModernAdvancementsPackets.FeedUpdatePacket> CODEC = StreamCodec.ofMember(
         ModernAdvancementsPackets.FeedUpdatePacket::write, ModernAdvancementsPackets.FeedUpdatePacket::read
      );

      private void write(RegistryFriendlyByteBuf buf) {
         buf.writeInt(this.events.size());

         for (FeedEvent e : this.events) {
            buf.writeUtf(e.playerName());
            buf.writeUtf(e.advancementId());
            buf.writeUtf(e.advancementTitle());
            buf.writeUtf(e.tabTitle());
            buf.writeLong(e.timestamp());
         }
      }

      private static ModernAdvancementsPackets.FeedUpdatePacket read(RegistryFriendlyByteBuf buf) {
         int size = buf.readInt();
         List<FeedEvent> list = new ArrayList<>(size);

         for (int i = 0; i < size; i++) {
            list.add(new FeedEvent(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readLong()));
         }

         return new ModernAdvancementsPackets.FeedUpdatePacket(list);
      }

      @NotNull
      public Type<? extends CustomPacketPayload> type() {
         return PACKET_TYPE;
      }
   }

   public record PlayerDetailPacket(UUID uuid, String playerName, List<TabDetail> tabs) implements CustomPacketPayload {
      public static final Type<ModernAdvancementsPackets.PlayerDetailPacket> PACKET_TYPE = new Type(
         Identifier.fromNamespaceAndPath("modern-advancements", "player_detail")
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, ModernAdvancementsPackets.PlayerDetailPacket> CODEC = StreamCodec.ofMember(
         ModernAdvancementsPackets.PlayerDetailPacket::write, ModernAdvancementsPackets.PlayerDetailPacket::read
      );

      private void write(RegistryFriendlyByteBuf buf) {
         buf.writeUUID(this.uuid);
         buf.writeUtf(this.playerName);
         buf.writeInt(this.tabs.size());

         for (TabDetail t : this.tabs) {
            buf.writeUtf(t.tabRootId().toString());
            buf.writeUtf(t.tabTitle());
            buf.writeInt(t.total());
            buf.writeInt(t.completedIds().size());

            for (Identifier id : t.completedIds()) {
               buf.writeUtf(id.toString());
            }
         }
      }

      private static ModernAdvancementsPackets.PlayerDetailPacket read(RegistryFriendlyByteBuf buf) {
         UUID uuid = buf.readUUID();
         String name = buf.readUtf();
         int tabCount = buf.readInt();
         List<TabDetail> tabs = new ArrayList<>(tabCount);

         for (int i = 0; i < tabCount; i++) {
            Identifier rootId = Identifier.parse(buf.readUtf());
            String title = buf.readUtf();
            int total = buf.readInt();
            int cCount = buf.readInt();
            List<Identifier> completed = new ArrayList<>(cCount);

            for (int j = 0; j < cCount; j++) {
               completed.add(Identifier.parse(buf.readUtf()));
            }

            tabs.add(new TabDetail(rootId, title, total, completed));
         }

         return new ModernAdvancementsPackets.PlayerDetailPacket(uuid, name, tabs);
      }

      @NotNull
      public Type<? extends CustomPacketPayload> type() {
         return PACKET_TYPE;
      }
   }

   public record PlayerListPacket(List<PlayerSummary> players) implements CustomPacketPayload {
      public static final Type<ModernAdvancementsPackets.PlayerListPacket> PACKET_TYPE = new Type(
         Identifier.fromNamespaceAndPath("modern-advancements", "player_list")
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, ModernAdvancementsPackets.PlayerListPacket> CODEC = StreamCodec.ofMember(
         ModernAdvancementsPackets.PlayerListPacket::write, ModernAdvancementsPackets.PlayerListPacket::read
      );

      private void write(RegistryFriendlyByteBuf buf) {
         buf.writeInt(this.players.size());

         for (PlayerSummary p : this.players) {
            buf.writeUUID(p.uuid());
            buf.writeUtf(p.name());
            buf.writeInt(p.completed());
            buf.writeInt(p.total());
         }
      }

      private static ModernAdvancementsPackets.PlayerListPacket read(RegistryFriendlyByteBuf buf) {
         int size = buf.readInt();
         List<PlayerSummary> list = new ArrayList<>(size);

         for (int i = 0; i < size; i++) {
            list.add(new PlayerSummary(buf.readUUID(), buf.readUtf(), buf.readInt(), buf.readInt()));
         }

         return new ModernAdvancementsPackets.PlayerListPacket(list);
      }

      @NotNull
      public Type<? extends CustomPacketPayload> type() {
         return PACKET_TYPE;
      }
   }

   public record RequestPlayerDetailPacket(UUID targetUuid) implements CustomPacketPayload {
      public static final Type<ModernAdvancementsPackets.RequestPlayerDetailPacket> PACKET_TYPE = new Type(
         Identifier.fromNamespaceAndPath("modern-advancements", "request_player_detail")
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, ModernAdvancementsPackets.RequestPlayerDetailPacket> CODEC = StreamCodec.ofMember(
         ModernAdvancementsPackets.RequestPlayerDetailPacket::write, ModernAdvancementsPackets.RequestPlayerDetailPacket::read
      );

      private void write(RegistryFriendlyByteBuf buf) {
         buf.writeUUID(this.targetUuid);
      }

      private static ModernAdvancementsPackets.RequestPlayerDetailPacket read(RegistryFriendlyByteBuf buf) {
         return new ModernAdvancementsPackets.RequestPlayerDetailPacket(buf.readUUID());
      }

      @NotNull
      public Type<? extends CustomPacketPayload> type() {
         return PACKET_TYPE;
      }
   }

   public record ServerAdvancementsPacket(List<AdvancementHolder> advancements, Map<Identifier, ModernAdvancementsPackets.SimpleRewards> rewards)
      implements CustomPacketPayload {
      public static final Type<ModernAdvancementsPackets.ServerAdvancementsPacket> PACKET_TYPE = new Type(
         Identifier.fromNamespaceAndPath("modern-advancements", "server_advancements")
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, ModernAdvancementsPackets.ServerAdvancementsPacket> CODEC = StreamCodec.ofMember(
         ModernAdvancementsPackets.ServerAdvancementsPacket::write, ModernAdvancementsPackets.ServerAdvancementsPacket::read
      );

      private void write(RegistryFriendlyByteBuf buf) {
         AdvancementHolder.LIST_STREAM_CODEC.encode(buf, this.advancements);
         buf.writeInt(this.rewards.size());

         for (Entry<Identifier, ModernAdvancementsPackets.SimpleRewards> entry : this.rewards.entrySet()) {
            buf.writeIdentifier(entry.getKey());
            ModernAdvancementsPackets.SimpleRewards r = entry.getValue();
            buf.writeInt(r.experience());
            buf.writeInt(r.loot().size());

            for (Identifier id : r.loot()) {
               buf.writeIdentifier(id);
            }

            buf.writeInt(r.recipes().size());

            for (Identifier id : r.recipes()) {
               buf.writeIdentifier(id);
            }

            buf.writeBoolean(r.hasFunction());
         }
      }

      @Contract("_ -> new")
      private static ModernAdvancementsPackets.ServerAdvancementsPacket read(RegistryFriendlyByteBuf buf) {
         List<AdvancementHolder> holders = (List<AdvancementHolder>)AdvancementHolder.LIST_STREAM_CODEC.decode(buf);
         int rewardCount = buf.readInt();
         Map<Identifier, ModernAdvancementsPackets.SimpleRewards> rewards = new HashMap<>(rewardCount);

         for (int i = 0; i < rewardCount; i++) {
            Identifier id = buf.readIdentifier();
            int xp = buf.readInt();
            int lootCount = buf.readInt();
            List<Identifier> loot = new ArrayList<>(lootCount);

            for (int j = 0; j < lootCount; j++) {
               loot.add(buf.readIdentifier());
            }

            int recipeCount = buf.readInt();
            List<Identifier> recipes = new ArrayList<>(recipeCount);

            for (int j = 0; j < recipeCount; j++) {
               recipes.add(buf.readIdentifier());
            }

            boolean hasFunction = buf.readBoolean();
            rewards.put(id, new ModernAdvancementsPackets.SimpleRewards(xp, loot, recipes, hasFunction));
         }

         return new ModernAdvancementsPackets.ServerAdvancementsPacket(holders, rewards);
      }

      @NotNull
      public Type<? extends CustomPacketPayload> type() {
         return PACKET_TYPE;
      }
   }

   public record SimpleRewards(int experience, List<Identifier> loot, List<Identifier> recipes, boolean hasFunction) {
      public boolean isEmpty() {
         return this.experience == 0 && this.loot.isEmpty() && this.recipes.isEmpty() && !this.hasFunction;
      }
   }
}
