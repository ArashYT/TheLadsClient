// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47) and CatConfig-MC (MIT). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.screen;

import com.thelads.core.v1_20_1.feature.screenshots.config.ConfigAccess;
import com.thelads.core.v1_20_1.feature.screenshots.config.ConfigOption;
import com.thelads.core.v1_20_1.feature.screenshots.config.ScreenshotViewerRenderedOptions;
import com.thelads.core.v1_20_1.feature.screenshots.config.ScreenshotViewerRenderedOptions.RenderedOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FastColor;

/**
 * The scrolling option rows of the settings screen: a collapsible yellow header per category and, below it, one 36 pixel row
 * per option (label on the left, its editor on the right).
 */
public final class ConfigOptionList extends ContainerObjectSelectionList<ConfigOptionList.Row> {
   private static final int ROW_HEIGHT = 36;
   private static final int INDENT = 10;
   private final List<Group> groups = new ArrayList<>();
   private boolean rebuild;

   public ConfigOptionList(Minecraft minecraft, int width, int top, int bottom) {
      super(minecraft, width, bottom - top, top, bottom, ROW_HEIGHT);
      this.setRenderBackground(false);
      this.setRenderTopAndBottom(false);
   }

   /** Fills the list with every rendered option, grouped in their categories. */
   public void addAll(ConfigAccess config, Predicate<ConfigOption<?>> optionChanged) {
      Map<String, Group> byCategory = new LinkedHashMap<>();
      for (RenderedOption<?> option : ScreenshotViewerRenderedOptions.options()) {
         AbstractWidget widget = option.widgetFactory().apply(config);
         if (widget == null) continue;
         Group group = byCategory.computeIfAbsent(option.option().category(), category -> new Group(this, category));
         group.rows.add(new OptionRow(this.minecraft, option, widget, () -> optionChanged.test(option.option())));
      }
      groups.addAll(byCategory.values());
      groups.sort((a, b) -> a.category.compareTo(b.category));
      rebuildNow();
   }

   private void rebuildNow() {
      rebuild = false;
      this.clearEntries();
      for (Group group : groups) {
         this.addEntry(group.header);
         if (group.open) {
            for (OptionRow row : group.rows) this.addEntry(row);
         }
      }
      this.setScrollAmount(this.getScrollAmount());
   }

   /** Called when a category was opened or closed; the list is rebuilt on the next frame, not in the middle of a click. */
   void requestRebuild() {
      rebuild = true;
   }

   public void tick() {
      for (Group group : groups) {
         for (OptionRow row : group.rows) {
            if (row.widget instanceof EditBox box) box.tick();
         }
      }
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      if (rebuild) rebuildNow();
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   @Override
   public int getRowLeft() {
      return this.x0;
   }

   @Override
   public int getRowWidth() {
      return this.width - 6;
   }

   @Override
   protected int getScrollbarPosition() {
      return this.x0 + this.width - 6;
   }

   private static final class Group {
      final String category;
      final CategoryRow header;
      final List<OptionRow> rows = new ArrayList<>();
      boolean open;

      Group(ConfigOptionList list, String category) {
         this.category = category;
         this.header = new CategoryRow(list.minecraft, this, ScreenshotViewerRenderedOptions.categoryName(category), list);
      }
   }

   public abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
      protected final Minecraft client;
      protected float hoveredTime;

      protected Row(Minecraft client) {
         this.client = client;
      }

      /** Fades the grey hover highlight in while the row is under the mouse and out again after. */
      protected void renderHighlight(GuiGraphics context, boolean nameHovered, int x, int y, int width) {
         if (nameHovered) {
            if (this.hoveredTime < 1.0F) this.hoveredTime = Math.min(1.0F, this.hoveredTime + 0.1F);
         } else if (this.hoveredTime > 0.0F) {
            this.hoveredTime = Math.max(0.0F, this.hoveredTime - 0.1F);
         }
         context.fill(x, y, x + width, y + ROW_HEIGHT, FastColor.ARGB32.color((int) (this.hoveredTime * 0.2 * 255.0), 65, 65, 65));
      }

      protected void tooltip(Component description) {
         if (description != null && !description.getString().isBlank() && this.client.screen != null) {
            this.client.screen.setTooltipForNextRenderPass(Tooltip.splitTooltip(this.client, description));
         }
      }
   }

   private static final class OptionRow extends Row {
      private final RenderedOption<?> option;
      final AbstractWidget widget;
      private final java.util.function.BooleanSupplier changed;
      boolean indented = true;

      OptionRow(Minecraft client, RenderedOption<?> option, AbstractWidget widget, java.util.function.BooleanSupplier changed) {
         super(client);
         this.option = option;
         this.widget = widget;
         this.changed = changed;
      }

      @Override
      public void render(GuiGraphics context, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
         int x = left + (this.indented ? INDENT : 0);
         int rowWidth = width - (this.indented ? INDENT : 0);
         boolean nameHovered = hovered && mouseY >= top && mouseY <= top + ROW_HEIGHT;
         if (nameHovered) this.tooltip(this.option.description());
         this.renderHighlight(context, nameHovered, x, top, rowWidth);
         Font font = this.client.font;
         MutableComponent label = this.option.name().copy().withStyle(style -> style.withItalic(this.changed.getAsBoolean()));
         context.drawString(font, label, x + 8, top + (ROW_HEIGHT - 9) / 2, -1);
         this.widget.setX(x + rowWidth - 8 - this.widget.getWidth());
         this.widget.setY(top + (ROW_HEIGHT - this.widget.getHeight()) / 2);
         this.widget.render(context, mouseX, mouseY, deltaTicks);
      }

      @Override
      public List<? extends NarratableEntry> narratables() {
         return List.of(this.widget);
      }

      @Override
      public List<? extends GuiEventListener> children() {
         return List.of(this.widget);
      }
   }

   private static final class CategoryRow extends Row {
      private final Group group;
      private final Component name;
      private final ConfigOptionList list;
      private int lastTop;

      CategoryRow(Minecraft client, Group group, Component name, ConfigOptionList list) {
         super(client);
         this.group = group;
         this.name = name;
         this.list = list;
      }

      @Override
      public void render(GuiGraphics context, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
         this.lastTop = top;
         for (OptionRow row : this.group.rows) row.indented = true;
         boolean nameHovered = hovered && mouseY >= top && mouseY <= top + ROW_HEIGHT;
         this.renderHighlight(context, nameHovered, left, top, width);
         context.drawString(this.client.font, this.name.copy().withStyle(ChatFormatting.YELLOW), left + 8, top + (ROW_HEIGHT - 9) / 2, -1);
      }

      @Override
      public boolean mouseClicked(double mouseX, double mouseY, int button) {
         if (mouseY <= this.lastTop + ROW_HEIGHT) {
            this.group.open = !this.group.open;
            this.list.requestRebuild();
            return true;
         }
         return false;
      }

      @Override
      public List<? extends NarratableEntry> narratables() {
         return List.of();
      }

      @Override
      public List<? extends GuiEventListener> children() {
         return List.of();
      }
   }
}
