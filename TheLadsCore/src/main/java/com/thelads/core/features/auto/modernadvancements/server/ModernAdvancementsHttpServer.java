package com.thelads.core.features.auto.modernadvancements.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancements;
import com.thelads.core.features.auto.modernadvancements.data.api.FeedEvent;
import com.thelads.core.features.auto.modernadvancements.data.api.PlayerSummary;
import com.thelads.core.features.auto.modernadvancements.data.api.TabDetail;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Unmodifiable;

public class ModernAdvancementsHttpServer {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private HttpServer httpServer;

   public void start(int port) {
      String host = ModernAdvancements.CONFIG.getHttpApiHost();

      try {
         this.httpServer = HttpServer.create(new InetSocketAddress(host, port), 10);
         this.httpServer.createContext("/api/advancements", this::handleAdvancements);
         this.httpServer.createContext("/api/players", this::handlePlayers);
         this.httpServer.createContext("/api/player", this::handlePlayer);
         this.httpServer.createContext("/api/stats", this::handleStats);
         this.httpServer.createContext("/api/feed", this::handleFeed);
         this.httpServer.setExecutor(Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "ModernAdvancements-HTTP");
            t.setDaemon(true);
            return t;
         }));
         this.httpServer.start();
         ModernAdvancements.onLog("Advancement API listening on {}:{}", host, port);
      } catch (IOException var4) {
         ModernAdvancements.onLog("Failed to start Advancement API on {}:{} — {}", host, port, var4.getMessage());
      }
   }

   public void stop() {
      if (this.httpServer != null) {
         this.httpServer.stop(0);
         this.httpServer = null;
      }
   }

   private boolean checkAuth(HttpExchange ex) throws IOException {
      String key = ModernAdvancements.CONFIG.getHttpApiKey();
      if (key != null && !key.isBlank()) {
         String auth = ex.getRequestHeaders().getFirst("Authorization");
         if (auth != null && auth.startsWith("Bearer ") && auth.substring(7).equals(key)) {
            return false;
         } else {
            this.respond(ex, 401, Map.of("error", "Unauthorized"));
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean checkMethodGet(HttpExchange ex) throws IOException {
      if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
         return false;
      } else {
         ex.getResponseHeaders().set("Allow", "GET");
         this.respond(ex, 405, Map.of("error", "Method not allowed"));
         return true;
      }
   }

   private Map<String, String> parseQuery(HttpExchange ex) {
      Map<String, String> params = new LinkedHashMap<>();
      String query = ex.getRequestURI().getRawQuery();
      if (query == null) {
         return params;
      } else {
         for (String pair : query.split("&")) {
            int eq = pair.indexOf(61);
            if (eq >= 0) {
               params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
         }

         return params;
      }
   }

   private void handleAdvancements(HttpExchange ex) throws IOException {
      if (!this.checkMethodGet(ex)) {
         if (!this.checkAuth(ex)) {
            AdvancementTree tree = ServerPlayerAdvancementTracker.getInstance().getAdvancementTree();
            if (tree == null) {
               this.respond(ex, 503, Map.of("error", "Server not ready"));
            } else {
               List<Map<String, Object>> list = new ArrayList<>();

               for (AdvancementNode node : tree.nodes()) {
                  if (!node.advancement().display().isEmpty()) {
                     Map<String, Object> entry = new LinkedHashMap<>();
                     entry.put("id", node.holder().id().toString());
                     entry.put("tab", node.root().holder().id().toString());
                     entry.put("parent", node.parent() != null ? node.parent().holder().id().toString() : null);
                     node.advancement().display().ifPresent(d -> {
                        entry.put("title", d.getTitle().getString());
                        entry.put("description", d.getDescription().getString());
                        entry.put("type", d.getType().name());
                     });
                     list.add(entry);
                  }
               }

               this.respond(ex, 200, Map.of("advancements", list));
            }
         }
      }
   }

   private void handlePlayers(HttpExchange ex) throws IOException {
      if (!this.checkMethodGet(ex)) {
         if (!this.checkAuth(ex)) {
            ServerPlayerAdvancementTracker tracker = ServerPlayerAdvancementTracker.getInstance();
            List<Map<String, Object>> players = new ArrayList<>();

            for (PlayerSummary s : tracker.buildLeaderboard()) {
               players.add(this.buildPlayerEntry(tracker, s));
            }

            this.respond(ex, 200, Map.of("players", players));
         }
      }
   }

   private void handlePlayer(HttpExchange ex) throws IOException {
      if (!this.checkMethodGet(ex)) {
         if (!this.checkAuth(ex)) {
            Map<String, String> params = this.parseQuery(ex);
            String name = params.get("name");
            if (name != null && !name.isBlank()) {
               ServerPlayerAdvancementTracker tracker = ServerPlayerAdvancementTracker.getInstance();
               Optional<UUID> found = tracker.findPlayerByName(name);
               if (found.isEmpty()) {
                  this.respond(ex, 404, Map.of("error", "Player not found"));
               } else {
                  UUID uuid = found.get();
                  int total = tracker.getServerAdvancementTotal();
                  int completed = (int)tracker.getPlayerCompleted().getOrDefault(uuid, new ConcurrentHashMap<>()).values().stream().filter(v -> v).count();
                  this.respond(
                     ex, 200, this.buildPlayerEntry(tracker, new PlayerSummary(uuid, tracker.getPlayerNames().getOrDefault(uuid, "Unknown"), completed, total))
                  );
               }
            } else {
               this.respond(ex, 400, Map.of("error", "Missing required query parameter: name"));
            }
         }
      }
   }

   private Map<String, Object> buildPlayerEntry(ServerPlayerAdvancementTracker tracker, PlayerSummary s) {
      Map<String, Object> p = new LinkedHashMap<>();
      p.put("uuid", s.uuid().toString());
      p.put("name", s.name());
      p.put("completed", s.completed());
      p.put("total", s.total());
      p.put("percentage", pct(s.percentage()));
      List<Map<String, Object>> tabList = new ArrayList<>();

      for (TabDetail t : tracker.buildTabDetails(s.uuid())) {
         Map<String, Object> tm = new LinkedHashMap<>();
         tm.put("id", t.tabRootId().toString());
         tm.put("title", t.tabTitle());
         tm.put("completed", t.completedIds().size());
         tm.put("total", t.total());
         tm.put("completedIds", t.completedIds().stream().map(Identifier::toString).toList());
         tabList.add(tm);
      }

      p.put("tabs", tabList);
      return p;
   }

   private void handleStats(HttpExchange ex) throws IOException {
      if (!this.checkMethodGet(ex)) {
         if (!this.checkAuth(ex)) {
            ServerPlayerAdvancementTracker tracker = ServerPlayerAdvancementTracker.getInstance();
            AdvancementTree tree = tracker.getAdvancementTree();
            if (tree == null) {
               this.respond(ex, 503, Map.of("error", "Server not ready"));
            } else {
               List<PlayerSummary> leaderboard = tracker.buildLeaderboard();
               int playerCount = leaderboard.size();
               double avgPct = leaderboard.stream().mapToDouble(PlayerSummary::percentage).average().orElse(0.0);
               Map<String, Integer> counts = new LinkedHashMap<>();
               Map<String, String> titles = new LinkedHashMap<>();

               for (AdvancementNode node : tree.nodes()) {
                  if (!node.advancement().display().isEmpty()) {
                     String id = node.holder().id().toString();
                     counts.put(id, 0);
                     titles.put(id, node.advancement().display().map(d -> d.getTitle().getString()).orElse(id));
                  }
               }

               for (Entry<UUID, ConcurrentHashMap<Identifier, Boolean>> pe : tracker.getPlayerCompleted().entrySet()) {
                  for (Entry<Identifier, Boolean> ce : pe.getValue().entrySet()) {
                     if (Boolean.TRUE.equals(ce.getValue())) {
                        counts.merge(ce.getKey().toString(), 1, Integer::sum);
                     }
                  }
               }

               Map<String, Object> stats = new LinkedHashMap<>();
               stats.put("totalPlayers", playerCount);
               stats.put("totalAdvancements", counts.size());
               stats.put("averageCompletion", pct((float)avgPct));
               stats.put("mostCompleted", this.rankEntries(counts, titles, playerCount, true));
               stats.put("leastCompleted", this.rankEntries(counts, titles, playerCount, false));
               this.respond(ex, 200, stats);
            }
         }
      }
   }

   private void handleFeed(HttpExchange ex) throws IOException {
      if (!this.checkMethodGet(ex)) {
         if (!this.checkAuth(ex)) {
            Map<String, String> params = this.parseQuery(ex);
            List<FeedEvent> all = ServerPlayerAdvancementTracker.getInstance().getRecentFeed();
            List<FeedEvent> result = all;
            String limitStr = params.get("limit");
            if (limitStr != null) {
               try {
                  int limit = Integer.parseInt(limitStr);
                  if (limit > 0 && limit < all.size()) {
                     result = all.subList(0, limit);
                  }
               } catch (NumberFormatException var11) {
               }
            }

            String player = params.get("player");
            if (player != null && !player.isBlank()) {
               String lc = player.toLowerCase();
               result = result.stream().filter(exx -> exx.playerName().toLowerCase().contains(lc)).toList();
            }

            List<Map<String, Object>> events = new ArrayList<>();

            for (FeedEvent e : result) {
               Map<String, Object> m = new LinkedHashMap<>();
               m.put("playerName", e.playerName());
               m.put("advancementId", e.advancementId());
               m.put("advancementTitle", e.advancementTitle());
               m.put("tabTitle", e.tabTitle());
               m.put("timestamp", e.timestamp());
               events.add(m);
            }

            this.respond(ex, 200, Map.of("events", events, "total", all.size()));
         }
      }
   }

   @Unmodifiable
   private List<Map<String, Object>> rankEntries(Map<String, Integer> counts, Map<String, String> titles, int playerCount, boolean desc) {
      return counts.entrySet().stream().sorted(desc ? Entry.<String, Integer>comparingByValue().reversed() : Entry.comparingByValue()).limit(10L).map(e -> {
         Map<String, Object> m = new LinkedHashMap<>();
         m.put("id", e.getKey());
         m.put("title", titles.getOrDefault(e.getKey(), e.getKey()));
         m.put("count", e.getValue());
         m.put("percentage", playerCount > 0 ? pct((float)e.getValue().intValue() / playerCount) : 0.0);
         return m;
      }).toList();
   }

   private void respond(HttpExchange ex, int status, Object body) throws IOException {
      byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
      ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
      ex.sendResponseHeaders(status, bytes.length);

      try (OutputStream os = ex.getResponseBody()) {
         os.write(bytes);
      }
   }

   private static double pct(float v) {
      return Math.round(v * 1000.0) / 10.0;
   }
}
