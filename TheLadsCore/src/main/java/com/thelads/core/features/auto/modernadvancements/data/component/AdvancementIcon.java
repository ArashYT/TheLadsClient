package com.thelads.core.features.auto.modernadvancements.data.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.thelads.core.features.auto.modernadvancements.client.screen.ModernAdvancementsScreen;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class AdvancementIcon {
   public static final int ICON_SIZE = 32;
   public final AdvancementNode advancement;
   public final DisplayInfo display;
   public final int x;
   public final int y;
   public final ItemStack icon;

   public AdvancementIcon(AdvancementNode advancement, DisplayInfo display, int x, int y) {
      this.advancement = advancement;
      this.display = display;
      this.x = x;
      this.y = y;
      this.icon = display.getIcon().create();
   }

   public void render(
      GuiGraphicsExtractor context,
      int screenX,
      int screenY,
      int scrollOffsetX,
      int scrollOffsetY,
      int mouseX,
      int mouseY,
      int realMouseX,
      int realMouseY,
      Font font,
      boolean suppressTooltips,
      @Nullable ModernAdvancementsScreen screen
   ) {
      this.renderAt(
         context, screenX, screenY, scrollOffsetX, scrollOffsetY, this.x, this.y, mouseX, mouseY, realMouseX, realMouseY, font, suppressTooltips, screen
      );
   }

   public void renderAt(
      GuiGraphicsExtractor context,
      int screenX,
      int screenY,
      int scrollOffsetX,
      int scrollOffsetY,
      int overrideX,
      int overrideY,
      int mouseX,
      int mouseY,
      int realMouseX,
      int realMouseY,
      Font font,
      boolean suppressTooltips,
      @Nullable ModernAdvancementsScreen screen
   ) {
      this.renderAt(
         context,
         screenX,
         screenY,
         scrollOffsetX,
         scrollOffsetY,
         overrideX,
         overrideY,
         mouseX,
         mouseY,
         realMouseX,
         realMouseY,
         font,
         suppressTooltips,
         screen,
         false,
         false
      );
   }

   public void renderAt(
      GuiGraphicsExtractor context,
      int screenX,
      int screenY,
      int scrollOffsetX,
      int scrollOffsetY,
      int overrideX,
      int overrideY,
      int mouseX,
      int mouseY,
      int realMouseX,
      int realMouseY,
      Font font,
      boolean suppressTooltips,
      @Nullable ModernAdvancementsScreen screen,
      boolean isRootLocked,
      boolean isDraggingThis
   ) {
      int renderX = screenX + overrideX - scrollOffsetX;
      int renderY = screenY + overrideY - scrollOffsetY;
      boolean completed = this.isCompleted(screen);
      boolean hidden = this.display.isHidden();
      boolean hovered = !suppressTooltips && this.isMouseOverAt(mouseX, mouseY, screenX, screenY, scrollOffsetX, scrollOffsetY, overrideX, overrideY);
      context.fill(renderX, renderY, renderX + 32, renderY + 32, completed ? -16759808 : -15066598);

      int frameColor = switch (this.display.getType()) {
         case CHALLENGE -> hidden ? -11206571 : -5635926;
         case GOAL -> hidden ? -8952320 : -22016;
         default -> completed ? -16711936 : (hidden ? -13421790 : -11184811);
      };
      context.outline(renderX, renderY, 32, 32, frameColor);
      if (isDraggingThis) {
         context.outline(renderX - 1, renderY - 1, 34, 34, -1);
         context.outline(renderX - 2, renderY - 2, 36, 36, -2130706433);
      } else if (hovered) {
         context.outline(renderX + 1, renderY + 1, 30, 30, frameColor);
      }

      if (!this.icon.isEmpty()) {
         context.item(this.icon, renderX + 16 - 8, renderY + 16 - 8);
      }

      if (hidden) {
         context.fill(renderX + 1, renderY + 1, renderX + 32 - 1, renderY + 32 - 1, -2012151791);
      }

      if (completed) {
         String checkmark = "✔";
         context.text(font, checkmark, renderX + 32 - font.width(checkmark) - 2, renderY + 2, -16711936, true);
      }

      if (isRootLocked) {
         context.fill(renderX + 1, renderY + 1, renderX + 32 - 1, renderY + 32 - 1, -2013265852);
         context.text(font, "\ud83d\udd12", renderX + 2, renderY + 2, -5592406, false);
      }

      if (hovered) {
         List<Component> tooltip = new ArrayList<>();

         int titleColor;
         int var26 = titleColor = switch (this.display.getType()) {
            case CHALLENGE -> 11141290;
            case GOAL -> 16755200;
            default -> 16777215;
         };
         tooltip.add(Component.literal(this.display.getTitle().getString()).withStyle(s -> s.withColor(titleColor)));
         tooltip.add(Component.literal(this.display.getDescription().getString()).withStyle(s -> s.withColor(11184810)));
         if (completed) {
            tooltip.add(Component.translatable("gui.advancements.text.completed").withStyle(s -> s.withColor(65280)));
         } else {
            tooltip.add(Component.translatable("gui.advancements.text.incomplete").withStyle(s -> s.withColor(8947848)));
         }

         if (hidden) {
            tooltip.add(Component.translatable("gui.advancements.text.hidden").withStyle(s -> s.withColor(6710852)));
         }

         if (isRootLocked) {
            tooltip.add(Component.translatable("gui.advancements.layout.edit.root_locked").withStyle(s -> s.withColor(6710954)));
         }

         context.setComponentTooltipForNextFrame(font, tooltip, realMouseX, realMouseY);
      }
   }

   public boolean isMouseOver(double mouseX, double mouseY, int screenX, int screenY, int scrollOffsetX, int scrollOffsetY) {
      return this.isMouseOverAt(mouseX, mouseY, screenX, screenY, scrollOffsetX, scrollOffsetY, this.x, this.y);
   }

   public boolean isMouseOverAt(double mouseX, double mouseY, int screenX, int screenY, int scrollOffsetX, int scrollOffsetY, int atX, int atY) {
      int renderX = screenX + atX - scrollOffsetX;
      int renderY = screenY + atY - scrollOffsetY;
      return mouseX >= renderX && mouseX <= renderX + 32 && mouseY >= renderY && mouseY <= renderY + 32;
   }

   public boolean matchesSearch(String query) {
      if (query.trim().isEmpty()) {
         return false;
      } else {
         String combined = (this.display.getTitle().getString() + " " + this.display.getDescription().getString()).toLowerCase(Locale.ROOT);
         return combined.contains(query.trim());
      }
   }

   public boolean isCompleted(@Nullable ModernAdvancementsScreen screen) {
      if (screen == null) {
         return false;
      } else {
         Minecraft client = Minecraft.getInstance();
         if (client.getConnection() == null) {
            return false;
         } else {
            ClientAdvancements handler = client.getConnection().getAdvancements();
            AdvancementTree tree = handler.getTree();
            if (tree.get(this.advancement.holder().id()) == null) {
               return false;
            } else {
               AdvancementProgress progress = screen.getAdvancementProgress(this.advancement.holder());
               if (progress == null) {
                  progress = screen.getAdvancementProgressById(this.advancement.holder().id());
               }

               return progress != null && progress.isDone();
            }
         }
      }
   }
}
