package com.thelads.core.features.auto.modernadvancements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.thelads.core.features.auto.modernadvancements.client.ModernAdvancementKeybinds;
import com.thelads.core.features.auto.modernadvancements.client.hud.TrackedAdvancementsHud;
import com.thelads.core.features.auto.modernadvancements.config.ModernAdvancementsClientConfig;
import com.thelads.core.features.auto.modernadvancements.data.api.FeedEvent;
import com.thelads.core.features.auto.modernadvancements.data.api.PlayerSummary;
import com.thelads.core.features.auto.modernadvancements.data.handler.AdvancementScreenshotManager;
import com.thelads.core.features.auto.modernadvancements.data.layout.TabLayoutOverrideManager;
import com.thelads.core.features.auto.modernadvancements.data.tracker.HudState;
import com.thelads.core.features.auto.modernadvancements.data.tracker.TrackingManager;
import com.thelads.core.features.auto.modernadvancements.network.ModernAdvancementsNetworking;
import com.thelads.core.features.auto.modernadvancements.network.ModernAdvancementsPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModernAdvancementsClient implements ClientModInitializer {
   public static final String CONFIG_PATH = "config/modern-advancements/modern-advancements_client.json";
   public static ModernAdvancementsClientConfig CONFIG = loadConfig();
   @Nullable
   public static AdvancementTree serverAdvancementTree = null;
   public static final Map<Identifier, ModernAdvancementsPackets.SimpleRewards> serverRewards = new HashMap<>();
   public static List<PlayerSummary> serverLeaderboard = new ArrayList<>();
   public static List<FeedEvent> serverFeed = new ArrayList<>();
   @Nullable
   public static ModernAdvancementsPackets.PlayerDetailPacket pendingDetailPacket = null;
   @Nullable
   public static Identifier pendingFocusAdvancementId = null;

   public void onInitializeClient() {
      ModernAdvancementsNetworking.registerClientHandlers();
      ModernAdvancementKeybinds.create();
      HudElementRegistry.attachElementAfter(
         VanillaHudElements.SCOREBOARD, Identifier.fromNamespaceAndPath("modern-advancements", "tracked_advancements"), new TrackedAdvancementsHud()
      );
      ClientTickEvents.END_CLIENT_TICK.register(AdvancementScreenshotManager::checkAndCapture);
      ClientPlayConnectionEvents.JOIN.register((Join)(var0, var1, client) -> {
         if (client.getConnection() != null) {
            TrackingManager.getInstance().loadForSession();
            TrackingManager.getInstance().activateAsListener(client.getConnection().getAdvancements());
            ClientPlayNetworking.send(new ModernAdvancementsPackets.ClientHandshakePacket(true));
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(var0, var1) -> {
         TrackingManager.getInstance().clearForDisconnect();
         AdvancementScreenshotManager.clearForDisconnect();
         serverAdvancementTree = null;
         serverLeaderboard = new ArrayList<>();
         serverFeed = new ArrayList<>();
         pendingDetailPacket = null;
         pendingFocusAdvancementId = null;
         HudState.clearForSession();
         TabLayoutOverrideManager.clearAll();
      });
   }

   public static void setServerAdvancementTree(@Nullable AdvancementTree tree) {
      serverAdvancementTree = tree;
   }

   @Nullable
   public static AdvancementTree getServerAdvancementTree() {
      return serverAdvancementTree;
   }

   public static boolean hasServerAdvancementData() {
      return serverAdvancementTree != null;
   }

   @NotNull
   private static ModernAdvancementsClientConfig loadConfig() {
      ModernAdvancementsClientConfig config = new ModernAdvancementsClientConfig();

      try {
         File configFile = new File("config/modern-advancements/modern-advancements_client.json");
         if (configFile.exists()) {
            Gson gson = new Gson();
            Reader reader = new FileReader(configFile);
            config = (ModernAdvancementsClientConfig)gson.fromJson(reader, ModernAdvancementsClientConfig.class);
            reader.close();
         } else {
            boolean ignored = configFile.getParentFile().mkdirs();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Writer writer = new FileWriter(configFile);
            gson.toJson(config, writer);
            writer.close();
         }
      } catch (Exception var5) {
         ModernAdvancements.onLog("Failed to load client config: {}", var5);
      }

      config.checkVersion();
      return config;
   }
}
