package com.thelads.core.features.auto.modernadvancements.data.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.thelads.core.features.auto.modernadvancements.client.screen.ModernAdvancementsScreen;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

public class AdvancementConnectionRenderer {
   private static final int ICON_SIZE = 32;
   private static final int LINE_THICKNESS = 2;
   @Nullable
   private final ModernAdvancementsScreen screen;

   public AdvancementConnectionRenderer(@Nullable ModernAdvancementsScreen screen) {
      this.screen = screen;
   }

   public void renderConnections(
      GuiGraphicsExtractor context, int screenX, int screenY, int scrollOffsetX, int scrollOffsetY, Map<AdvancementHolder, AdvancementIcon> icons
   ) {
      for (AdvancementIcon icon : icons.values()) {
         AdvancementNode parent = icon.advancement.parent();
         if (parent != null) {
            AdvancementIcon parentIcon = icons.get(parent.holder());
            if (parentIcon != null) {
               this.drawConnection(context, screenX, screenY, scrollOffsetX, scrollOffsetY, parentIcon, icon);
            }
         }
      }
   }

   private void drawConnection(
      GuiGraphicsExtractor context, int screenX, int screenY, int scrollOffsetX, int scrollOffsetY, AdvancementIcon from, AdvancementIcon to
   ) {
      int x1 = screenX + from.x + 16 - scrollOffsetX;
      int y1 = screenY + from.y + 16 - scrollOffsetY;
      int x2 = screenX + to.x + 16 - scrollOffsetX;
      int y2 = screenY + to.y + 16 - scrollOffsetY;
      boolean childComplete = to.isCompleted(this.screen);
      int lineColor = childComplete ? -16733696 : -12566464;
      if (x1 != x2 && y1 != y2) {
         for (int i = 0; i < 2; i++) {
            context.horizontalLine(Math.min(x1, x2), Math.max(x1, x2), y1 + i, lineColor);
         }

         for (int i = 0; i < 2; i++) {
            context.verticalLine(x2 + i, Math.min(y1, y2), Math.max(y1, y2), lineColor);
         }
      } else if (x1 == x2) {
         for (int i = 0; i < 2; i++) {
            context.verticalLine(x1 + i, Math.min(y1, y2), Math.max(y1, y2), lineColor);
         }
      } else {
         for (int i = 0; i < 2; i++) {
            context.horizontalLine(Math.min(x1, x2), Math.max(x1, x2), y1 + i, lineColor);
         }
      }
   }

   public void renderConnectionsSpine(
      GuiGraphicsExtractor context, int screenX, int screenY, int scrollOffsetX, int scrollOffsetY, Map<AdvancementHolder, AdvancementIcon> icons
   ) {
      Map<AdvancementIcon, List<AdvancementIcon>> parentToChildren = new LinkedHashMap<>();

      for (AdvancementIcon icon : icons.values()) {
         AdvancementNode parentNode = icon.advancement.parent();
         if (parentNode != null) {
            AdvancementIcon parentIcon = icons.get(parentNode.holder());
            if (parentIcon != null) {
               parentToChildren.computeIfAbsent(parentIcon, var0 -> new ArrayList<>()).add(icon);
            }
         }
      }

      for (Entry<AdvancementIcon, List<AdvancementIcon>> entry : parentToChildren.entrySet()) {
         this.drawSpineConnection(
            context, screenX, screenY, scrollOffsetX, scrollOffsetY, entry.getKey(), entry.getValue(), entry.getKey().x, entry.getKey().y, null
         );
      }
   }

   public void renderConnectionsSpineWithOverrides(
      GuiGraphicsExtractor context,
      int screenX,
      int screenY,
      int scrollOffsetX,
      int scrollOffsetY,
      Map<AdvancementHolder, AdvancementIcon> icons,
      Map<AdvancementHolder, int[]> posOverrides
   ) {
      Map<AdvancementIcon, List<AdvancementIcon>> parentToChildren = new LinkedHashMap<>();

      for (AdvancementIcon icon : icons.values()) {
         AdvancementNode parentNode = icon.advancement.parent();
         if (parentNode != null) {
            AdvancementIcon parentIcon = icons.get(parentNode.holder());
            if (parentIcon != null) {
               parentToChildren.computeIfAbsent(parentIcon, var0 -> new ArrayList<>()).add(icon);
            }
         }
      }

      for (Entry<AdvancementIcon, List<AdvancementIcon>> entry : parentToChildren.entrySet()) {
         AdvancementIcon parent = entry.getKey();
         int[] parentPos = posOverrides.getOrDefault(parent.advancement.holder(), new int[]{parent.x, parent.y});
         this.drawSpineConnection(context, screenX, screenY, scrollOffsetX, scrollOffsetY, parent, entry.getValue(), parentPos[0], parentPos[1], posOverrides);
      }
   }

   private void drawSpineConnection(
      GuiGraphicsExtractor context,
      int screenX,
      int screenY,
      int scrollOffsetX,
      int scrollOffsetY,
      AdvancementIcon parent,
      List<AdvancementIcon> children,
      int parentX,
      int parentY,
      @Nullable Map<AdvancementHolder, int[]> posOverrides
   ) {
      if (!children.isEmpty()) {
         int px = screenX + parentX + 16 - scrollOffsetX;
         int py = screenY + parentY + 16 - scrollOffsetY;
         int[] firstChildPos = this.resolvePos(children.getFirst(), posOverrides);
         int parentRight = screenX + parentX + 32 - scrollOffsetX;
         int childLeft = screenX + firstChildPos[0] - scrollOffsetX;
         int spineX = (parentRight + childLeft) / 2;
         boolean parentComplete = parent.isCompleted(this.screen);
         int parentColor = parentComplete ? -16733696 : -12566464;

         for (int i = 0; i < 2; i++) {
            context.horizontalLine(Math.min(px, spineX), Math.max(px, spineX), py + i, parentColor);
         }

         int spineMinY = py;
         int spineMaxY = py;

         for (AdvancementIcon child : children) {
            int[] cPos = this.resolvePos(child, posOverrides);
            int cy = screenY + cPos[1] + 16 - scrollOffsetY;
            if (cy < spineMinY) {
               spineMinY = cy;
            }

            if (cy > spineMaxY) {
               spineMaxY = cy;
            }
         }

         if (spineMinY < spineMaxY) {
            for (int i = 0; i < 2; i++) {
               context.verticalLine(spineX + i, spineMinY, spineMaxY, parentColor);
            }
         }

         for (AdvancementIcon child : children) {
            int[] cPosx = this.resolvePos(child, posOverrides);
            int cx = screenX + cPosx[0] + 16 - scrollOffsetX;
            int cyx = screenY + cPosx[1] + 16 - scrollOffsetY;
            boolean childComplete = child.isCompleted(this.screen);
            int childColor = childComplete ? -16733696 : -12566464;

            for (int i = 0; i < 2; i++) {
               context.horizontalLine(Math.min(spineX, cx), Math.max(spineX, cx), cyx + i, childColor);
            }
         }
      }
   }

   private int[] resolvePos(AdvancementIcon icon, @Nullable Map<AdvancementHolder, int[]> overrides) {
      if (overrides != null) {
         int[] pos = overrides.get(icon.advancement.holder());
         if (pos != null) {
            return pos;
         }
      }

      return new int[]{icon.x, icon.y};
   }
}
