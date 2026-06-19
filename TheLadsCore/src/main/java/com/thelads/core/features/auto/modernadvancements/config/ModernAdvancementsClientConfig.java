package com.thelads.core.features.auto.modernadvancements.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancements;
import com.thelads.core.features.auto.modernadvancements.data.layout.LayoutMode;
import com.thelads.core.features.auto.modernadvancements.data.toast.ToastAnimationStyle;
import com.thelads.core.features.auto.modernadvancements.data.toast.ToastBackgroundStyle;
import com.thelads.core.features.auto.modernadvancements.data.toast.ToastDisplayStyle;
import com.thelads.core.features.auto.modernadvancements.data.toast.ToastFrameStyle;
import com.thelads.core.features.auto.modernadvancements.data.toast.ToastIconLayout;
import com.thelads.core.features.auto.modernadvancements.data.toast.ToastSize;
import com.thelads.core.features.auto.modernadvancements.data.tracker.HudAnchor;
import com.thelads.core.features.auto.modernadvancements.data.tracker.TrackerDisplayMode;
import com.thelads.core.features.auto.modernadvancements.data.tracker.TrackerSize;

public class ModernAdvancementsClientConfig {
   private int version = 5;
   private boolean hideTracking = false;
   private boolean removeCompletedTracking = false;
   private boolean autoTrackPathway = false;
   private boolean takeScreenshots = true;
   private boolean matchWindowSize = false;
   private HudAnchor boundingBoxAnchor = HudAnchor.CENTER_LEFT;
   private int boundingBoxOffsetX = 0;
   private int boundingBoxOffsetY = 0;
   private TrackerSize trackerSize = TrackerSize.MEDIUM;
   private TrackerDisplayMode trackerDisplayMode = TrackerDisplayMode.NORMAL;
   private ToastSize toastSize = ToastSize.MEDIUM;
   private ToastAnimationStyle toastAnimation = ToastAnimationStyle.SLIDE;
   private ToastFrameStyle toastFrameStyle = ToastFrameStyle.AUTO;
   private ToastBackgroundStyle toastBackground = ToastBackgroundStyle.SOLID;
   private ToastIconLayout toastIconLayout = ToastIconLayout.LEFT_SMALL;
   private HudAnchor toastAnchor = HudAnchor.TOP_RIGHT;
   private LayoutMode layoutMode = LayoutMode.MODERN;
   private int toastOffsetX = 0;
   private int toastOffsetY = 0;
   private ToastDisplayStyle toastDisplayStyle = ToastDisplayStyle.SINGLE;
   private long toastDurationMs = 5000L;
   private boolean toastSoundTask = false;
   private boolean toastSoundChallenge = true;

   public boolean hideTracker() {
      return this.hideTracking;
   }

   public void hideTracker(boolean v) {
      this.hideTracking = v;
   }

   public boolean removeCompletedTracking() {
      return this.removeCompletedTracking;
   }

   public void removeCompletedTracking(boolean v) {
      this.removeCompletedTracking = v;
   }

   public boolean trackPathways() {
      return this.autoTrackPathway;
   }

   public void trackPathways(boolean v) {
      this.autoTrackPathway = v;
   }

   public boolean takeScreenshots() {
      return this.takeScreenshots;
   }

   public void takeScreenshots(boolean v) {
      this.takeScreenshots = v;
   }

   public boolean matchWindowSize() {
      return this.matchWindowSize;
   }

   public void matchWindowSize(boolean v) {
      this.matchWindowSize = v;
   }

   public HudAnchor boundingBoxAnchor() {
      return this.boundingBoxAnchor != null ? this.boundingBoxAnchor : HudAnchor.CENTER_LEFT;
   }

   public void boundingBoxAnchor(HudAnchor v) {
      this.boundingBoxAnchor = v;
   }

   public void boundingBoxOffset(int x, int y) {
      this.boundingBoxOffsetX = x;
      this.boundingBoxOffsetY = y;
   }

   public int boundingBoxOffsetX() {
      return this.boundingBoxOffsetX;
   }

   public int boundingBoxOffsetY() {
      return this.boundingBoxOffsetY;
   }

   public TrackerSize trackerSize() {
      return this.trackerSize != null ? this.trackerSize : TrackerSize.MEDIUM;
   }

   public void trackerSize(TrackerSize v) {
      this.trackerSize = v;
   }

   public TrackerDisplayMode trackerDisplayMode() {
      return this.trackerDisplayMode != null ? this.trackerDisplayMode : TrackerDisplayMode.NORMAL;
   }

   public void trackerDisplayMode(TrackerDisplayMode v) {
      this.trackerDisplayMode = v;
   }

   public ToastSize toastSize() {
      return this.toastSize != null ? this.toastSize : ToastSize.MEDIUM;
   }

   public void toastSize(ToastSize v) {
      this.toastSize = v;
   }

   public ToastAnimationStyle toastAnimation() {
      return this.toastAnimation != null ? this.toastAnimation : ToastAnimationStyle.SLIDE;
   }

   public void toastAnimation(ToastAnimationStyle v) {
      this.toastAnimation = v;
   }

   public ToastFrameStyle toastFrameStyle() {
      return this.toastFrameStyle != null ? this.toastFrameStyle : ToastFrameStyle.AUTO;
   }

   public void toastFrameStyle(ToastFrameStyle v) {
      this.toastFrameStyle = v;
   }

   public ToastBackgroundStyle toastBackground() {
      return this.toastBackground != null ? this.toastBackground : ToastBackgroundStyle.SOLID;
   }

   public void toastBackground(ToastBackgroundStyle v) {
      this.toastBackground = v;
   }

   public ToastIconLayout toastIconLayout() {
      return this.toastIconLayout != null ? this.toastIconLayout : ToastIconLayout.LEFT_SMALL;
   }

   public void toastIconLayout(ToastIconLayout v) {
      this.toastIconLayout = v;
   }

   public HudAnchor toastAnchor() {
      return this.toastAnchor != null ? this.toastAnchor : HudAnchor.TOP_RIGHT;
   }

   public void toastAnchor(HudAnchor v) {
      this.toastAnchor = v;
   }

   public LayoutMode layoutMode() {
      return this.layoutMode != null ? this.layoutMode : LayoutMode.VANILLA;
   }

   public void layoutMode(LayoutMode v) {
      this.layoutMode = v;
   }

   public int toastOffsetX() {
      return this.toastOffsetX;
   }

   public int toastOffsetY() {
      return this.toastOffsetY;
   }

   public void toastOffset(int x, int y) {
      this.toastOffsetX = x;
      this.toastOffsetY = y;
   }

   public ToastDisplayStyle toastDisplayStyle() {
      return this.toastDisplayStyle;
   }

   public void toastDisplayStyle(ToastDisplayStyle v) {
      this.toastDisplayStyle = v;
   }

   public long toastDurationMs() {
      return this.toastDurationMs > 0L ? this.toastDurationMs : 5000L;
   }

   public void toastDurationMs(long v) {
      this.toastDurationMs = v;
   }

   public boolean toastSoundTask() {
      return this.toastSoundTask;
   }

   public void toastSoundTask(boolean v) {
      this.toastSoundTask = v;
   }

   public boolean toastSoundChallenge() {
      return this.toastSoundChallenge;
   }

   public void toastSoundChallenge(boolean v) {
      this.toastSoundChallenge = v;
   }

   public void save() {
      try {
         File configFile = new File("config/modern-advancements/modern-advancements_client.json");
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
      int oldVersion = this.getVersion();
      int currentVersion = new ModernAdvancementsClientConfig().getVersion();
      if (oldVersion != currentVersion) {
         this.setVersionChanges(oldVersion);
      }
   }

   private void setVersionChanges(int oldVersion) {
      if (oldVersion < 1) {
         oldVersion++;
      }

      if (oldVersion < 2) {
         if (this.toastSize == null) {
            this.toastSize = ToastSize.MEDIUM;
         }

         if (this.toastAnimation == null) {
            this.toastAnimation = ToastAnimationStyle.SLIDE;
         }

         if (this.toastFrameStyle == null) {
            this.toastFrameStyle = ToastFrameStyle.AUTO;
         }

         if (this.toastBackground == null) {
            this.toastBackground = ToastBackgroundStyle.SOLID;
         }

         if (this.toastIconLayout == null) {
            this.toastIconLayout = ToastIconLayout.LEFT_SMALL;
         }

         if (this.toastAnchor == null) {
            this.toastAnchor = HudAnchor.TOP_RIGHT;
         }

         if (this.toastDisplayStyle == null) {
            this.toastDisplayStyle = ToastDisplayStyle.SINGLE;
         }

         oldVersion++;
      }

      if (oldVersion < 3) {
         if (this.layoutMode == null) {
            this.layoutMode = LayoutMode.MODERN;
         }

         oldVersion++;
      }

      if (oldVersion < 4) {
         this.takeScreenshots = true;
         this.matchWindowSize = false;
         oldVersion++;
      }

      if (oldVersion < 5) {
         if (this.trackerSize == null) {
            this.trackerSize = TrackerSize.MEDIUM;
         }

         if (this.trackerDisplayMode == null) {
            this.trackerDisplayMode = TrackerDisplayMode.NORMAL;
         }

         oldVersion++;
      }

      this.setVersion(oldVersion);
      this.save();
   }

   public int getVersion() {
      return this.version;
   }

   public void setVersion(int v) {
      this.version = v;
   }
}
