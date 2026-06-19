package com.thelads.core.features.auto.modernadvancements.data.layout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import com.thelads.core.features.auto.modernadvancements.data.component.ModernAdvancementTab;
import net.minecraft.advancements.AdvancementHolder;
import org.jetbrains.annotations.Nullable;

public class LayoutEditSession {
   private static final int GRID_SIZE = 50;
   private static final int ICON_SIZE = 32;
   private final ModernAdvancementTab tab;
   private final AdvancementHolder rootHolder;
   private final Map<AdvancementHolder, int[]> originalPositions;
   private final Map<AdvancementHolder, int[]> pendingPositions;
   private final Map<AdvancementHolder, int[]> modernBasePositions;
   @Nullable
   private AdvancementHolder dragging = null;
   private double dragStartWorldX = 0.0;
   private double dragStartWorldY = 0.0;
   private int dragStartIconX = 0;
   private int dragStartIconY = 0;

   public LayoutEditSession(ModernAdvancementTab tab, Map<AdvancementHolder, int[]> modernBasePositions) {
      this.tab = tab;
      this.rootHolder = tab.getRoot() != null ? tab.getRoot().holder() : null;
      this.modernBasePositions = Collections.unmodifiableMap(new LinkedHashMap<>(modernBasePositions));
      Map<AdvancementHolder, int[]> current = tab.getAllPositions();
      this.originalPositions = Collections.unmodifiableMap(new LinkedHashMap<>(current));
      this.pendingPositions = new LinkedHashMap<>(current);
   }

   public boolean tryStartDrag(double worldX, double worldY) {
      for (Entry<AdvancementHolder, int[]> entry : this.pendingPositions.entrySet()) {
         AdvancementHolder holder = entry.getKey();
         int[] pos = entry.getValue();
         if (worldX >= pos[0] && worldX <= pos[0] + 32 && worldY >= pos[1] && worldY <= pos[1] + 32) {
            if (holder.equals(this.rootHolder)) {
               return false;
            }

            this.dragging = holder;
            this.dragStartWorldX = worldX;
            this.dragStartWorldY = worldY;
            this.dragStartIconX = pos[0];
            this.dragStartIconY = pos[1];
            return true;
         }
      }

      return false;
   }

   public void updateDrag(double worldX, double worldY) {
      if (this.dragging != null) {
         int snappedX = (int)(Math.round((this.dragStartIconX + (worldX - this.dragStartWorldX)) / 50.0) * 50L);
         int snappedY = (int)(Math.round((this.dragStartIconY + (worldY - this.dragStartWorldY)) / 50.0) * 50L);

         for (Entry<AdvancementHolder, int[]> entry : this.pendingPositions.entrySet()) {
            if (!entry.getKey().equals(this.dragging)) {
               int[] pos = entry.getValue();
               if (pos[0] == snappedX && pos[1] == snappedY) {
                  return;
               }
            }
         }

         this.pendingPositions.put(this.dragging, new int[]{snappedX, snappedY});
      }
   }

   public void endDrag() {
      this.dragging = null;
   }

   public boolean isDragging() {
      return this.dragging != null;
   }

   @Nullable
   public AdvancementHolder getDragging() {
      return this.dragging;
   }

   public void reset() {
      this.pendingPositions.clear();
      this.pendingPositions.putAll(this.originalPositions);
      this.dragging = null;
   }

   public void applyDefault() {
      this.pendingPositions.clear();
      this.pendingPositions.putAll(this.modernBasePositions);
      this.dragging = null;
   }

   public TabLayoutOverride buildOverride() {
      TabLayoutOverride override = new TabLayoutOverride();

      for (Entry<AdvancementHolder, int[]> entry : this.pendingPositions.entrySet()) {
         int[] pending = entry.getValue();
         int[] base = this.modernBasePositions.get(entry.getKey());
         if (base == null || pending[0] != base[0] || pending[1] != base[1]) {
            override.advancements.put(entry.getKey().id().toString(), new int[]{pending[0], pending[1]});
         }
      }

      return override;
   }

   public Map<AdvancementHolder, int[]> getPendingPositions() {
      return Collections.unmodifiableMap(this.pendingPositions);
   }

   public ModernAdvancementTab getTab() {
      return this.tab;
   }
}
