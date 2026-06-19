package com.thelads.core.features.auto.modernadvancements.data.layout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;

public class ModernLayout {
   private static final int GRID_SIZE = 50;
   private static final int MIN_ROW_GAP = 1;
   private static final int CHAIN_COL_GAP = 1;
   private static final int BRANCH_COL_GAP = 1;
   private Map<AdvancementHolder, List<AdvancementNode>> sortedChildren;
   private final Map<AdvancementHolder, Integer> leafSlots = new HashMap<>();
   private final Map<AdvancementHolder, Double> rowCache = new HashMap<>();
   private final Map<AdvancementHolder, Integer> colMap = new HashMap<>();
   private int leafCounter = 0;

   public Map<AdvancementHolder, int[]> compute(AdvancementNode root, Collection<AdvancementNode> allNodes) {
      this.sortedChildren = this.buildSortedChildrenMap(allNodes);
      this.leafSlots.clear();
      this.rowCache.clear();
      this.colMap.clear();
      this.leafCounter = 0;
      this.assignLeafSlots(root);
      this.computeRow(root);
      this.colMap.put(root.holder(), 0);
      this.assignColumns(root);
      this.snapAndResolve();
      return this.buildResult(allNodes);
   }

   private void assignLeafSlots(AdvancementNode node) {
      List<AdvancementNode> kids = this.sortedChildren.getOrDefault(node.holder(), Collections.emptyList());
      if (kids.isEmpty()) {
         this.leafSlots.put(node.holder(), this.leafCounter++);
      } else {
         for (AdvancementNode kid : kids) {
            this.assignLeafSlots(kid);
         }
      }
   }

   private double computeRow(AdvancementNode node) {
      Double cached = this.rowCache.get(node.holder());
      if (cached != null) {
         return cached;
      } else {
         List<AdvancementNode> kids = this.sortedChildren.getOrDefault(node.holder(), Collections.emptyList());
         double row;
         if (kids.isEmpty()) {
            row = this.leafSlots.get(node.holder()).intValue();
         } else {
            double sum = 0.0;

            for (AdvancementNode kid : kids) {
               sum += this.computeRow(kid);
            }

            row = sum / kids.size();
         }

         this.rowCache.put(node.holder(), row);
         return row;
      }
   }

   private void assignColumns(AdvancementNode node) {
      int col = this.colMap.get(node.holder());
      List<AdvancementNode> kids = this.sortedChildren.getOrDefault(node.holder(), Collections.emptyList());
      if (!kids.isEmpty()) {
         int gap = kids.size() == 1 ? 1 : 1;
         int nextCol = col + gap;

         for (AdvancementNode kid : kids) {
            this.colMap.put(kid.holder(), nextCol);
            this.assignColumns(kid);
         }
      }
   }

   private void snapAndResolve() {
      Map<AdvancementHolder, Integer> intRows = new HashMap<>();

      for (Entry<AdvancementHolder, Double> e : this.rowCache.entrySet()) {
         intRows.put(e.getKey(), (int)Math.round(e.getValue()));
      }

      for (Entry<AdvancementHolder, Integer> e : intRows.entrySet()) {
         this.rowCache.put(e.getKey(), (double)e.getValue().intValue());
      }

      Map<Integer, List<AdvancementHolder>> byCol = new HashMap<>();

      for (Entry<AdvancementHolder, Integer> e : this.colMap.entrySet()) {
         Double row = this.rowCache.get(e.getKey());
         if (row != null) {
            byCol.computeIfAbsent(e.getValue(), var0 -> new ArrayList<>()).add(e.getKey());
         }
      }

      for (List<AdvancementHolder> holders : byCol.values()) {
         holders.sort(Comparator.comparingDouble(h -> this.rowCache.getOrDefault(h, 0.0)));

         for (int i = 1; i < holders.size(); i++) {
            double prevRow = this.rowCache.get(holders.get(i - 1));
            double currRow = this.rowCache.get(holders.get(i));
            double needed = prevRow + 1.0;
            if (currRow < needed) {
               double delta = needed - currRow;

               for (int j = i; j < holders.size(); j++) {
                  this.rowCache.put(holders.get(j), this.rowCache.get(holders.get(j)) + delta);
               }
            }
         }
      }
   }

   private Map<AdvancementHolder, int[]> buildResult(Collection<AdvancementNode> allNodes) {
      int minCol = this.colMap.values().stream().mapToInt(v -> v).min().orElse(0);
      int minRow = this.rowCache.values().stream().mapToInt(v -> (int)Math.round(v)).min().orElse(0);
      Map<AdvancementHolder, int[]> result = new HashMap<>();

      for (AdvancementNode node : allNodes) {
         Integer col = this.colMap.get(node.holder());
         Double row = this.rowCache.get(node.holder());
         if (col != null && row != null) {
            result.put(node.holder(), new int[]{(col - minCol) * 50, ((int)Math.round(row) - minRow) * 50});
         }
      }

      return result;
   }

   private Map<AdvancementHolder, List<AdvancementNode>> buildSortedChildrenMap(Collection<AdvancementNode> allNodes) {
      Map<AdvancementHolder, List<AdvancementNode>> map = new HashMap<>();

      for (AdvancementNode node : allNodes) {
         if (node.parent() != null) {
            map.computeIfAbsent(node.parent().holder(), var0 -> new ArrayList<>()).add(node);
         }
      }

      for (List<AdvancementNode> children : map.values()) {
         children.sort((a, b) -> {
            String ta = a.advancement().display().map(d -> d.getTitle().getString()).orElse("");
            String tb = b.advancement().display().map(d -> d.getTitle().getString()).orElse("");
            return ta.compareToIgnoreCase(tb);
         });
      }

      return map;
   }
}
