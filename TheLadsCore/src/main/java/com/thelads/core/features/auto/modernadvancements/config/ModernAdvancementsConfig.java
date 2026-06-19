package com.thelads.core.features.auto.modernadvancements.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancements;

public class ModernAdvancementsConfig {
   public int version = 2;
   public boolean sendAdvancementData = true;
   public boolean httpApiEnabled = false;
   public String httpApiHost = "0.0.0.0";
   public int httpApiPort = 25580;
   public String httpApiKey = "";
   public int feedHistoryLimit = 0;

   public void save() {
      try {
         File configFile = new File("config/modern-advancements/modern-advancements_config.json");
         boolean ignored = configFile.getParentFile().mkdirs();
         Gson gson = new GsonBuilder().setPrettyPrinting().create();
         Writer writer = new FileWriter(configFile);
         gson.toJson(this, writer);
         writer.close();
      } catch (Exception var5) {
         ModernAdvancements.onLog("Failed to save config: {}", var5);
      }
   }

   public void checkVersion() {
      int oldVersion = this.version;
      int currentVersion = (new ModernAdvancementsConfig()).version;
      if (oldVersion != currentVersion) {
         this.setVersionChanges(oldVersion);
      }
   }

   private void setVersionChanges(int oldVersion) {
      if (oldVersion < 1) {
         oldVersion++;
      }

      if (oldVersion < 2) {
         this.httpApiEnabled = false;
         this.httpApiHost = "0.0.0.0";
         this.httpApiPort = 25580;
         this.httpApiKey = "";
         this.feedHistoryLimit = 0;
         oldVersion++;
      }

      this.version = oldVersion;
      this.save();
   }

   public int getVersion() {
      return this.version;
   }

   public void setVersion(int value) {
      this.version = value;
   }

   public boolean shouldSendAdvancementData() {
      return this.sendAdvancementData;
   }

   public void setSendAdvancementData(boolean value) {
      this.sendAdvancementData = value;
   }

   public boolean isHttpApiEnabled() {
      return this.httpApiEnabled;
   }

   public void setHttpApiEnabled(boolean value) {
      this.httpApiEnabled = value;
   }

   public String getHttpApiHost() {
      return this.httpApiHost != null && !this.httpApiHost.isBlank() ? this.httpApiHost : "0.0.0.0";
   }

   public void setHttpApiHost(String value) {
      this.httpApiHost = value;
   }

   public int getHttpApiPort() {
      return this.httpApiPort;
   }

   public void setHttpApiPort(int value) {
      this.httpApiPort = value;
   }

   public String getHttpApiKey() {
      return this.httpApiKey;
   }

   public void setHttpApiKey(String value) {
      this.httpApiKey = value;
   }

   public int getFeedHistoryLimit() {
      return this.feedHistoryLimit;
   }

   public void setFeedHistoryLimit(int value) {
      this.feedHistoryLimit = value;
   }

   public boolean isFeedUnlimited() {
      return this.feedHistoryLimit <= 0;
   }
}
