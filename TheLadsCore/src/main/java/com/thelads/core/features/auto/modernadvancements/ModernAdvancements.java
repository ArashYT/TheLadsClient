package com.thelads.core.features.auto.modernadvancements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import com.thelads.core.features.auto.modernadvancements.config.ModernAdvancementsConfig;
import com.thelads.core.features.auto.modernadvancements.network.ModernAdvancementsNetworking;
import com.thelads.core.features.auto.modernadvancements.server.ModernAdvancementsHttpServer;
import com.thelads.core.features.auto.modernadvancements.server.ServerPlayerAdvancementTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModernAdvancements implements ModInitializer {
   public static final String MOD_ID = "modern-advancements";
   public static final String MOD_NAME = "Modern Advancements";
   private static final Logger LOGGER = LoggerFactory.getLogger("Modern Advancements");
   public static final ModernAdvancementsConfig CONFIG = loadConfig();
   public static final String CONFIG_PATH = "config/modern-advancements/modern-advancements_config.json";
   private static final ModernAdvancementsHttpServer HTTP_SERVER = new ModernAdvancementsHttpServer();

   public void onInitialize() {
      onLog("Loading {}...", "Modern Advancements");
      ModernAdvancementsNetworking.registerPayloads();
      ModernAdvancementsNetworking.registerServerHandlers();
      ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> {
         ServerPlayerAdvancementTracker.getInstance().onServerStarted(server);
         if (CONFIG.isHttpApiEnabled()) {
            HTTP_SERVER.start(CONFIG.getHttpApiPort());
         }
      });
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)var0 -> {
         HTTP_SERVER.stop();
         ServerPlayerAdvancementTracker.getInstance().onServerStopped();
      });
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, var1, server) -> {
         ServerPlayer player = handler.getPlayer();
         server.execute(() -> ServerPlayerAdvancementTracker.getInstance().onPlayerJoin(player));
      });
      ServerPlayConnectionEvents.DISCONNECT
         .register((Disconnect)(handler, var1) -> ServerPlayerAdvancementTracker.getInstance().onPlayerDisconnect(handler.getPlayer().getUUID()));
      onLog("Loaded {}", "Modern Advancements");
   }

   public static void onLog(String message, Object... args) {
      LOGGER.info(message, args);
   }

   @NotNull
   private static ModernAdvancementsConfig loadConfig() {
      ModernAdvancementsConfig config = new ModernAdvancementsConfig();

      try {
         File configFile = new File("config/modern-advancements/modern-advancements_config.json");
         if (configFile.exists()) {
            Gson gson = new Gson();
            Reader reader = new FileReader(configFile);
            config = (ModernAdvancementsConfig)gson.fromJson(reader, ModernAdvancementsConfig.class);
            reader.close();
         } else {
            boolean ignored = configFile.getParentFile().mkdirs();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Writer writer = new FileWriter(configFile);
            gson.toJson(config, writer);
            writer.close();
         }
      } catch (Exception var5) {
         onLog("Failed to load config: {}", var5);
      }

      config.checkVersion();
      return config;
   }
}
