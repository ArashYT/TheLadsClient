package com.thelads.core.features.auto.modernadvancements.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancements;
import com.thelads.core.features.auto.modernadvancements.data.api.FeedEvent;
import com.thelads.core.features.auto.modernadvancements.data.api.PlayerSummary;
import com.thelads.core.features.auto.modernadvancements.data.api.TabDetail;
import com.thelads.core.features.auto.modernadvancements.network.ModernAdvancementsPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ServerPlayerAdvancementTracker {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path PLAYER_PATH = Path.of("config", "modern-advancements", "api/player-progress.json");
   private static final Path FEED_PATH = Path.of("config", "modern-advancements", "api/feed-history.json");
   private static ServerPlayerAdvancementTracker instance;
   private final Set<UUID> modPlayers = ConcurrentHashMap.newKeySet();
   private final ConcurrentHashMap<UUID, ConcurrentHashMap<Identifier, Boolean>> playerCompleted = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<UUID, String> playerNames = new ConcurrentHashMap<>();
   private final CopyOnWriteArrayList<FeedEvent> feed = new CopyOnWriteArrayList<>();
   private volatile MinecraftServer server;
   private volatile AdvancementTree advancementTree;
   private volatile int serverAdvancementTotal = 0;

   private ServerPlayerAdvancementTracker() {
   }

   public static ServerPlayerAdvancementTracker getInstance() {
      if (instance == null) {
         instance = new ServerPlayerAdvancementTracker();
      }

      return instance;
   }

   public void onServerStarted(MinecraftServer server) {
      this.server = server;
      AdvancementTree tree = new AdvancementTree();
      tree.addAll(server.getAdvancements().getAllAdvancements());
      this.advancementTree = tree;
      this.serverAdvancementTotal = this.countDisplayable(tree);
      this.loadPersisted();
      this.loadFeed();
   }

   public void onServerStopped() {
      this.persistPlayers();
      this.persistFeed();
      this.server = null;
      this.advancementTree = null;
      this.serverAdvancementTotal = 0;
      this.modPlayers.clear();
   }

   public void registerModPlayer(UUID uuid) {
      this.modPlayers.add(uuid);
   }

   public void onPlayerJoin(ServerPlayer player) {
      UUID uuid = player.getUUID();
      this.playerNames.put(uuid, player.getName().getString());
      MinecraftServer srv = this.server;
      if (srv != null) {
         ConcurrentHashMap<Identifier, Boolean> progress = this.playerCompleted.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());

         for (AdvancementHolder holder : srv.getAdvancements().getAllAdvancements()) {
            if (!holder.value().display().isEmpty()) {
               AdvancementProgress p = player.getAdvancements().getOrStartProgress(holder);
               progress.put(holder.id(), p.isDone());
            }
         }
      }

      this.persistPlayers();
   }

   public void onPlayerDisconnect(UUID uuid) {
      this.modPlayers.remove(uuid);
      this.persistPlayers();
   }

   public void onAdvancementGranted(ServerPlayer player, AdvancementHolder advancement) {
      if (!advancement.value().display().isEmpty()) {
         this.playerCompleted.computeIfAbsent(player.getUUID(), var0 -> new ConcurrentHashMap<>()).put(advancement.id(), true);
         String advTitle = advancement.value().display().map(d -> d.getTitle().getString()).orElse(advancement.id().toString());
         String tabTitle = "";
         AdvancementTree tree = this.advancementTree;
         if (tree != null) {
            AdvancementNode node = tree.get(advancement.id());
            if (node != null) {
               tabTitle = node.root().advancement().display().map(d -> d.getTitle().getString()).orElse("");
            }
         }

         FeedEvent event = new FeedEvent(player.getName().getString(), advancement.id().toString(), advTitle, tabTitle, System.currentTimeMillis());
         this.feed.addFirst(event);
         this.cullFeed();
         this.persistFeed();
         this.broadcastToModPlayers(new ModernAdvancementsPackets.PlayerListPacket(this.buildLeaderboard()));
         this.broadcastToModPlayers(new ModernAdvancementsPackets.FeedUpdatePacket(List.of(event)));
      }
   }

   private void cullFeed() {
      int limit = ModernAdvancements.CONFIG.getFeedHistoryLimit();
      if (limit > 0 && this.feed.size() > limit) {
         List<FeedEvent> culled = new ArrayList<>(this.feed.subList(0, limit));
         this.feed.clear();
         this.feed.addAll(culled);
      }
   }

   private void broadcastToModPlayers(CustomPacketPayload packet) {
      MinecraftServer srv = this.server;
      if (srv != null) {
         for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (this.modPlayers.contains(p.getUUID())) {
               ServerPlayNetworking.send(p, packet);
            }
         }
      }
   }

   public void sendInitialData(ServerPlayer target) {
      ServerPlayNetworking.send(target, new ModernAdvancementsPackets.PlayerListPacket(this.buildLeaderboard()));
      ServerPlayNetworking.send(target, new ModernAdvancementsPackets.FeedUpdatePacket(new ArrayList<>(this.feed)));
   }

   public void handleDetailRequest(ServerPlayer requester, UUID targetUuid) {
      ServerPlayNetworking.send(
         requester,
         new ModernAdvancementsPackets.PlayerDetailPacket(targetUuid, this.playerNames.getOrDefault(targetUuid, "Unknown"), this.buildTabDetails(targetUuid))
      );
   }

   public List<PlayerSummary> buildLeaderboard() {
      int total = this.serverAdvancementTotal;
      List<PlayerSummary> result = new ArrayList<>();

      for (Entry<UUID, ConcurrentHashMap<Identifier, Boolean>> entry : this.playerCompleted.entrySet()) {
         int completed = (int)entry.getValue().values().stream().filter(v -> v).count();
         result.add(new PlayerSummary(entry.getKey(), this.playerNames.getOrDefault(entry.getKey(), "Unknown"), completed, total));
      }

      result.sort(Comparator.comparingDouble(PlayerSummary::percentage).reversed());
      return result;
   }

   public List<TabDetail> buildTabDetails(UUID uuid) {
      AdvancementTree tree = this.advancementTree;
      if (tree == null) {
         return Collections.emptyList();
      } else {
         Map<Identifier, Boolean> progress = this.playerCompleted.getOrDefault(uuid, new ConcurrentHashMap<>());
         Map<Identifier, List<Identifier>> completedByTab = new LinkedHashMap<>();
         Map<Identifier, String> tabTitles = new LinkedHashMap<>();
         Map<Identifier, Integer> tabTotals = new LinkedHashMap<>();

         for (AdvancementNode root : tree.roots()) {
            if (!root.advancement().display().isEmpty()) {
               Identifier id = root.holder().id();
               completedByTab.put(id, new ArrayList<>());
               tabTitles.put(id, root.advancement().display().map(d -> d.getTitle().getString()).orElse(id.toString()));
               tabTotals.put(id, 0);
            }
         }

         for (AdvancementNode node : tree.nodes()) {
            if (!node.advancement().display().isEmpty()) {
               Identifier rootId = node.root().holder().id();
               if (completedByTab.containsKey(rootId)) {
                  tabTotals.merge(rootId, 1, Integer::sum);
                  if (Boolean.TRUE.equals(progress.get(node.holder().id()))) {
                     completedByTab.get(rootId).add(node.holder().id());
                  }
               }
            }
         }

         List<TabDetail> result = new ArrayList<>();

         for (Identifier rootId : completedByTab.keySet()) {
            result.add(new TabDetail(rootId, tabTitles.get(rootId), tabTotals.get(rootId), completedByTab.get(rootId)));
         }

         return result;
      }
   }

   private int countDisplayable(AdvancementTree tree) {
      int count = 0;

      for (AdvancementNode node : tree.nodes()) {
         if (node.advancement().display().isPresent()) {
            count++;
         }
      }

      return count;
   }

   private void persistPlayers() {
      try {
         Files.createDirectories(PLAYER_PATH.getParent());
         ServerPlayerAdvancementTracker.PersistedPlayers data = new ServerPlayerAdvancementTracker.PersistedPlayers();
         data.players = new LinkedHashMap<>();

         for (Entry<UUID, ConcurrentHashMap<Identifier, Boolean>> e : this.playerCompleted.entrySet()) {
            ServerPlayerAdvancementTracker.PlayerRecord rec = new ServerPlayerAdvancementTracker.PlayerRecord();
            rec.name = this.playerNames.getOrDefault(e.getKey(), "Unknown");
            rec.completed = e.getValue().entrySet().stream().filter(Entry::getValue).map(ce -> ce.getKey().toString()).toList();
            data.players.put(e.getKey().toString(), rec);
         }

         try (Writer w = new OutputStreamWriter(Files.newOutputStream(PLAYER_PATH), StandardCharsets.UTF_8)) {
            GSON.toJson(data, w);
         }
      } catch (Exception var7) {
         ModernAdvancements.onLog("Failed to persist player data: {}", var7.getMessage());
      }
   }

   private void loadPersisted() {
      if (Files.exists(PLAYER_PATH)) {
         try {
            try (Reader r = new InputStreamReader(Files.newInputStream(PLAYER_PATH), StandardCharsets.UTF_8)) {
               Type type = (new TypeToken<ServerPlayerAdvancementTracker.PersistedPlayers>() {}).getType();
               ServerPlayerAdvancementTracker.PersistedPlayers data = (ServerPlayerAdvancementTracker.PersistedPlayers)GSON.fromJson(r, type);
               if (data != null && data.players != null) {
                  for (Entry<String, ServerPlayerAdvancementTracker.PlayerRecord> e : data.players.entrySet()) {
                     UUID uuid;
                     try {
                        uuid = UUID.fromString(e.getKey());
                     } catch (Exception var13) {
                        continue;
                     }

                     this.playerNames.put(uuid, e.getValue().name != null ? e.getValue().name : "Unknown");
                     ConcurrentHashMap<Identifier, Boolean> map = new ConcurrentHashMap<>();
                     if (e.getValue().completed != null) {
                        for (String id : e.getValue().completed) {
                           try {
                              map.put(Identifier.parse(id), true);
                           } catch (Exception var12) {
                           }
                        }
                     }

                     this.playerCompleted.put(uuid, map);
                  }

                  ModernAdvancements.onLog("Loaded persisted data for {} players", data.players.size());
                  return;
               }
            }
         } catch (Exception var15) {
            ModernAdvancements.onLog("Failed to load persisted player data: {}", var15.getMessage());
         }
      }
   }

   private void persistFeed() {
      try {
         Files.createDirectories(FEED_PATH.getParent());

         try (Writer w = new OutputStreamWriter(Files.newOutputStream(FEED_PATH), StandardCharsets.UTF_8)) {
            GSON.toJson(new ArrayList<>(this.feed), w);
         }
      } catch (Exception var6) {
         ModernAdvancements.onLog("Failed to persist feed: {}", var6.getMessage());
      }
   }

   private void loadFeed() {
      if (Files.exists(FEED_PATH)) {
         try {
            try (Reader r = new InputStreamReader(Files.newInputStream(FEED_PATH), StandardCharsets.UTF_8)) {
               Type type = (new TypeToken<List<FeedEvent>>() {}).getType();
               List<FeedEvent> loaded = (List<FeedEvent>)GSON.fromJson(r, type);
               if (loaded != null) {
                  this.feed.clear();
                  this.feed.addAll(loaded);
                  this.cullFeed();
                  ModernAdvancements.onLog("Loaded {} feed events", this.feed.size());
                  return;
               }
            }
         } catch (Exception var6) {
            ModernAdvancements.onLog("Failed to load feed history: {}", var6.getMessage());
         }
      }
   }

   public MinecraftServer getServer() {
      return this.server;
   }

   public List<FeedEvent> getRecentFeed() {
      return Collections.unmodifiableList(this.feed);
   }

   public Map<UUID, ConcurrentHashMap<Identifier, Boolean>> getPlayerCompleted() {
      return Collections.unmodifiableMap(this.playerCompleted);
   }

   public Map<UUID, String> getPlayerNames() {
      return Collections.unmodifiableMap(this.playerNames);
   }

   public AdvancementTree getAdvancementTree() {
      return this.advancementTree;
   }

   public int getServerAdvancementTotal() {
      return this.serverAdvancementTotal;
   }

   public Optional<UUID> findPlayerByName(String name) {
      for (Entry<UUID, String> e : this.playerNames.entrySet()) {
         if (e.getValue().equalsIgnoreCase(name)) {
            return Optional.of(e.getKey());
         }
      }

      return Optional.empty();
   }

   private static class PersistedPlayers {
      Map<String, ServerPlayerAdvancementTracker.PlayerRecord> players;
   }

   private static class PlayerRecord {
      String name;
      List<String> completed;
   }
}
