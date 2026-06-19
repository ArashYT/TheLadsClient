package com.thelads.core.features.auto.modernadvancements.data.layout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancements;
import net.minecraft.resources.Identifier;

public class TabLayoutOverrideManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Map<Identifier, TabLayoutOverride> cache = new HashMap<>();

   private TabLayoutOverrideManager() {
   }

   public static TabLayoutOverride getOrLoad(Identifier rootId) {
      return cache.computeIfAbsent(rootId, TabLayoutOverrideManager::load);
   }

   public static void save(Identifier rootId, TabLayoutOverride override) {
      Path path = overridePath(rootId);

      try {
         Files.createDirectories(path.getParent());

         try (Writer w = new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            GSON.toJson(override, w);
         }

         cache.put(rootId, override);
         ModernAdvancements.onLog("Saved layout override for {}", rootId);
      } catch (Exception var8) {
         ModernAdvancements.onLog("Failed to save layout override for {}: {}", rootId, var8.getMessage());
      }
   }

   private static TabLayoutOverride load(Identifier rootId) {
      Path path = overridePath(rootId);
      if (!Files.exists(path)) {
         return new TabLayoutOverride();
      } else {
         try {
            TabLayoutOverride var4;
            try (Reader r = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
               TabLayoutOverride loaded = (TabLayoutOverride)GSON.fromJson(r, TabLayoutOverride.class);
               if (loaded == null) {
                  return new TabLayoutOverride();
               }

               if (loaded.advancements == null) {
                  loaded.advancements = new LinkedHashMap<>();
               }

               var4 = loaded;
            }

            return var4;
         } catch (Exception var7) {
            ModernAdvancements.onLog("Failed to load layout override for {}: {}", rootId, var7.getMessage());
            return new TabLayoutOverride();
         }
      }
   }

   public static void clearAll() {
      cache.clear();
   }

   private static Path overridePath(Identifier rootId) {
      String safePath = rootId.getPath().replace('/', '_').replace(':', '_');
      return Path.of("config", "modern-advancements", "overrides", rootId.getNamespace(), safePath + ".json");
   }
}
