package com.thelads.core.features.auto.modernadvancements.data.tracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancements;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientAdvancements.Listener;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

public class TrackingManager implements Listener {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static TrackingManager instance;
   private String sessionId;
   private final List<Identifier> trackedAdvancements = new ArrayList<>();
   private final Map<AdvancementHolder, AdvancementProgress> progressCache = new HashMap<>();
   private final Map<Identifier, Integer> requirementCycleIndices = new HashMap<>();

   private TrackingManager() {
   }

   public static TrackingManager getInstance() {
      if (instance == null) {
         instance = new TrackingManager();
      }

      return instance;
   }

   public void loadForSession() {
      String newId = resolveSessionId();
      if (!newId.equals(this.sessionId)) {
         this.sessionId = newId;
         this.trackedAdvancements.clear();
         this.progressCache.clear();
         Path path = this.getFilePath();
         if (Files.exists(path)) {
            try (Reader reader = new FileReader(path.toFile())) {
               TrackingManager.TrackingData data = (TrackingManager.TrackingData)GSON.fromJson(reader, TrackingManager.TrackingData.class);
               if (data != null && data.tracked != null) {
                  for (String id : data.tracked) {
                     try {
                        this.trackedAdvancements.add(Identifier.parse(id));
                     } catch (Exception var9) {
                     }
                  }
               }

               if (data != null && data.requirementIndices != null) {
                  data.requirementIndices.forEach((k, v) -> {
                     try {
                        this.requirementCycleIndices.put(Identifier.parse(k), v);
                     } catch (Exception var4x) {
                     }
                  });
               }
            } catch (Exception var11) {
               ModernAdvancements.onLog("Failed to load tracking data: {}", var11);
            }
         }
      }
   }

   private void save() {
      if (this.sessionId != null) {
         try {
            Path path = this.getFilePath();
            Files.createDirectories(path.getParent());
            TrackingManager.TrackingData data = new TrackingManager.TrackingData();
            data.tracked = this.trackedAdvancements.stream().<String>map(Identifier::toString).toList();
            data.requirementIndices = new HashMap<>();
            this.requirementCycleIndices.forEach((k, v) -> data.requirementIndices.put(k.toString(), v));

            try (Writer writer = new FileWriter(path.toFile())) {
               GSON.toJson(data, writer);
            }
         } catch (Exception var8) {
            ModernAdvancements.onLog("Failed to save tracking data: {}", var8);
         }
      }
   }

   public void track(Identifier id) {
      if (!this.trackedAdvancements.contains(id)) {
         this.trackedAdvancements.add(id);
         HudState.trackEntryAdded(id);
         this.save();
      }
   }

   public void untrack(Identifier id) {
      if (this.trackedAdvancements.remove(id)) {
         HudState.trackEntryRemoved(id);
         this.save();
      }
   }

   public boolean isTracked(Identifier id) {
      return this.trackedAdvancements.contains(id);
   }

   public List<Identifier> getTracked() {
      return Collections.unmodifiableList(this.trackedAdvancements);
   }

   public void updateProgress(AdvancementNode node, AdvancementProgress progress) {
      this.progressCache.put(node.holder(), progress);
   }

   @Nullable
   public AdvancementProgress getProgress(AdvancementHolder holder) {
      return this.progressCache.get(holder);
   }

   public void activateAsListener(ClientAdvancements handler) {
      handler.setListener(this);
   }

   public void clearForDisconnect() {
      this.sessionId = null;
      this.progressCache.clear();
   }

   public void rotateHighlightForward() {
      if (!this.trackedAdvancements.isEmpty()) {
         Identifier first = this.trackedAdvancements.removeFirst();
         this.trackedAdvancements.add(first);
         this.save();
         HudState.resetAllScroll();
      }
   }

   public void rotateHighlightBackward() {
      if (!this.trackedAdvancements.isEmpty()) {
         Identifier last = this.trackedAdvancements.removeLast();
         this.trackedAdvancements.addFirst(last);
         this.save();
         HudState.resetAllScroll();
      }
   }

   public int getRequirementIndex(Identifier id) {
      return this.requirementCycleIndices.getOrDefault(id, 0);
   }

   public void cycleRequirementForward(Identifier id, int count) {
      if (count > 0) {
         int cur = this.requirementCycleIndices.getOrDefault(id, 0);
         this.requirementCycleIndices.put(id, (cur + 1) % count);
         HudState.resetScroll(id + "_req");
         this.save();
      }
   }

   public void cycleRequirementForward(Identifier id, List<String> allReqs, @Nullable AdvancementProgress progress) {
      int count = allReqs.size();
      if (count > 0) {
         int cur = this.requirementCycleIndices.getOrDefault(id, 0) % count;

         for (int i = 1; i <= count; i++) {
            int next = (cur + i) % count;
            CriterionProgress cp = progress != null ? progress.getCriterion(allReqs.get(next)) : null;
            if (cp == null || !cp.isDone()) {
               this.requirementCycleIndices.put(id, next);
               HudState.resetScroll(id + "_req");
               this.save();
               return;
            }
         }

         this.requirementCycleIndices.put(id, (cur + 1) % count);
         HudState.resetScroll(id + "_req");
         this.save();
      }
   }

   public void cycleRequirementBackward(Identifier id, int count) {
      if (count > 0) {
         int cur = this.requirementCycleIndices.getOrDefault(id, 0);
         this.requirementCycleIndices.put(id, (cur - 1 + count) % count);
         HudState.resetScroll(id + "_req");
         this.save();
      }
   }

   public void cycleRequirementBackward(Identifier id, List<String> allReqs, @Nullable AdvancementProgress progress) {
      int count = allReqs.size();
      if (count > 0) {
         int cur = this.requirementCycleIndices.getOrDefault(id, 0) % count;

         for (int i = 1; i <= count; i++) {
            int prev = (cur - i + count) % count;
            CriterionProgress cp = progress != null ? progress.getCriterion(allReqs.get(prev)) : null;
            if (cp == null || !cp.isDone()) {
               this.requirementCycleIndices.put(id, prev);
               HudState.resetScroll(id + "_req");
               this.save();
               return;
            }
         }

         this.requirementCycleIndices.put(id, (cur - 1 + count) % count);
         HudState.resetScroll(id + "_req");
         this.save();
      }
   }

   public void advanceToNextIncomplete(Identifier id, AdvancementNode node, AdvancementProgress progress) {
      List<String> allReqs = HudState.getAllRequirementsForCycling(node);
      if (!allReqs.isEmpty()) {
         int count = allReqs.size();
         int cur = this.requirementCycleIndices.getOrDefault(id, 0) % count;
         String curReq = allReqs.get(cur);
         CriterionProgress curCp = progress.getCriterion(curReq);
         if (curCp != null && curCp.isDone()) {
            for (int i = 1; i < count; i++) {
               int next = (cur + i) % count;
               CriterionProgress cp = progress.getCriterion(allReqs.get(next));
               if (cp == null || !cp.isDone()) {
                  this.requirementCycleIndices.put(id, next);
                  HudState.resetScroll(id + "_req");
                  this.save();
                  return;
               }
            }
         }
      }
   }

   public void onAddAdvancementRoot(AdvancementNode root) {
   }

   public void onRemoveAdvancementRoot(AdvancementNode root) {
   }

   public void onAddAdvancementTask(AdvancementNode dependent) {
   }

   public void onRemoveAdvancementTask(AdvancementNode dependent) {
   }

   public void onAdvancementsCleared() {
      this.progressCache.clear();
   }

   public void onSelectedTabChanged(@Nullable AdvancementHolder advancement) {
   }

   public void onUpdateAdvancementProgress(AdvancementNode advancement, AdvancementProgress progress) {
      AdvancementProgress previous = this.progressCache.get(advancement.holder());
      boolean wasDone = previous != null && previous.isDone();
      this.progressCache.put(advancement.holder(), progress);
      if (progress.isDone() && !wasDone) {
         HudState.trackEntryCompleted(advancement.holder().id());
         boolean wasTracked = this.isTracked(advancement.holder().id());
         if (wasTracked && ModernAdvancementsClient.CONFIG.trackPathways()) {
            for (AdvancementNode child : advancement.children()) {
               if (child.advancement().display().isPresent()) {
                  AdvancementProgress childProgress = this.progressCache.get(child.holder());
                  boolean childAlreadyDone = childProgress != null && childProgress.isDone();
                  if (!childAlreadyDone) {
                     this.track(child.holder().id());
                  }
               }
            }
         }

         if (wasTracked && ModernAdvancementsClient.CONFIG.removeCompletedTracking()) {
            this.untrack(advancement.holder().id());
         }
      } else if (!progress.isDone() && this.isTracked(advancement.holder().id())) {
         this.advanceToNextIncomplete(advancement.holder().id(), advancement, progress);
      }
   }

   private Path getFilePath() {
      return Path.of("config", "modern-advancements", "tracking", sanitize(this.sessionId) + ".json");
   }

   private static String resolveSessionId() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getCurrentServer() != null) {
         return "server_" + mc.getCurrentServer().ip;
      } else if (mc.getSingleplayerServer() != null) {
         try {
            return "world_" + mc.getSingleplayerServer().getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent().getFileName().toString();
         } catch (Exception var2) {
            return "world_" + mc.getSingleplayerServer().getWorldData().getLevelName();
         }
      } else {
         return "unknown";
      }
   }

   private static String sanitize(String input) {
      return input.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
   }

   private static class TrackingData {
      List<String> tracked;
      Map<String, Integer> requirementIndices;
   }
}
