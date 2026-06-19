package com.thelads.core.features.auto.modernadvancements.data.handler;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import com.thelads.core.features.auto.modernadvancements.client.screen.popup.DetailPanelPopUpScreen;
import com.thelads.core.features.auto.modernadvancements.data.component.ModernAdvancementTab;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public class ViewportHandler {
   private static final int ICON_SIZE = 32;
   private static final int ADVANCEMENT_AREA_TOP = 70;
   private static final float MIN_ZOOM = 0.45F;
   private static final float MAX_ZOOM = 2.0F;
   private static final float ZOOM_STEP = 0.1F;
   private float zoomLevel = 1.0F;
   private int scrollX = 0;
   private int scrollY = 0;
   private final Map<AdvancementHolder, Integer> lastScrollX = new LinkedHashMap<>();
   private final Map<AdvancementHolder, Integer> lastScrollY = new LinkedHashMap<>();

   public float getZoom() {
      return this.zoomLevel;
   }

   public int getScrollX() {
      return this.scrollX;
   }

   public int getScrollY() {
      return this.scrollY;
   }

   public void setZoom(float zoom) {
      this.zoomLevel = zoom;
   }

   public void setScroll(int x, int y) {
      this.scrollX = x;
      this.scrollY = y;
   }

   public boolean hasScrollFor(AdvancementHolder entry) {
      return this.lastScrollX.containsKey(entry);
   }

   public void restoreScrollFor(AdvancementHolder entry) {
      this.scrollX = this.lastScrollX.get(entry);
      this.scrollY = this.lastScrollY.getOrDefault(entry, 0);
   }

   public void putScrollFor(AdvancementHolder entry, int x, int y) {
      this.lastScrollX.put(entry, x);
      this.lastScrollY.put(entry, y);
   }

   public void saveScrollForTab(@Nullable ModernAdvancementTab tab) {
      if (tab != null && tab.getRoot() != null) {
         this.putScrollFor(tab.getRoot().holder(), this.scrollX, this.scrollY);
      }
   }

   public void centerOnRoot(@Nullable ModernAdvancementTab tab, int screenWidth, int screenHeight, boolean detailOpen) {
      if (tab != null && tab.getRoot() != null) {
         int[] pos = tab.getRootPosition();
         int advAreaRight = screenWidth - (detailOpen ? DetailPanelPopUpScreen.getPanelWidth() : 0);
         this.scrollX = (int)(pos[0] * this.zoomLevel) + 20 + 16 - advAreaRight / 2;
         this.scrollY = (int)(pos[1] * this.zoomLevel) + 70 + 20 + 16 - (70 + screenHeight - 40) / 2;
         this.putScrollFor(tab.getRoot().holder(), this.scrollX, this.scrollY);
      }
   }

   public void centerOnNode(@Nullable AdvancementNode node, @Nullable ModernAdvancementTab tab, int screenWidth, int screenHeight, boolean detailOpen) {
      if (tab != null && node != null) {
         int[] pos = tab.getPositionOf(node.holder());
         if (pos != null) {
            int advAreaRight = screenWidth - (detailOpen ? DetailPanelPopUpScreen.getPanelWidth() : 0);
            this.scrollX = (int)(pos[0] * this.zoomLevel) + 20 + 16 - advAreaRight / 2;
            this.scrollY = (int)(pos[1] * this.zoomLevel) + 70 + 20 + 16 - (70 + screenHeight - 40) / 2;
            if (tab.getRoot() != null) {
               this.putScrollFor(tab.getRoot().holder(), this.scrollX, this.scrollY);
            }
         }
      }
   }

   public boolean applyZoom(double amount, double mouseX, double mouseY) {
      float oldZoom = this.zoomLevel;
      this.zoomLevel = Math.clamp(this.zoomLevel + (float)amount * 0.1F, 0.45F, 2.0F);
      if (oldZoom == this.zoomLevel) {
         return false;
      } else {
         int worldX = (int)((mouseX - 20.0 + this.scrollX) / oldZoom);
         int worldY = (int)((mouseY - 90.0 + this.scrollY) / oldZoom);
         this.scrollX = (int)(worldX * this.zoomLevel - (mouseX - 20.0));
         this.scrollY = (int)(worldY * this.zoomLevel - (mouseY - 90.0));
         return true;
      }
   }

   public Map<Identifier, int[]> buildScrollSnapshot() {
      Map<Identifier, int[]> result = new HashMap<>();

      for (Entry<AdvancementHolder, Integer> e : this.lastScrollX.entrySet()) {
         result.put(e.getKey().id(), new int[]{e.getValue(), this.lastScrollY.getOrDefault(e.getKey(), 0)});
      }

      return result;
   }

   public void restoreScrollEntries(Map<Identifier, int[]> tabScrolls, Iterable<AdvancementHolder> knownHolders) {
      for (AdvancementHolder holder : knownHolders) {
         int[] vals = tabScrolls.get(holder.id());
         if (vals != null) {
            this.putScrollFor(holder, vals[0], vals[1]);
         }
      }
   }
}
