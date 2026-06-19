package com.thelads.core.features.auto.modernadvancements.client.screen;

import java.util.ArrayList;
import java.util.List;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.client.hud.TrackedAdvancementsHud;
import com.thelads.core.features.auto.modernadvancements.data.tracker.HudAnchor;
import com.thelads.core.features.auto.modernadvancements.data.tracker.HudState;
import com.thelads.core.features.auto.modernadvancements.data.tracker.TrackerDisplayMode;
import com.thelads.core.features.auto.modernadvancements.data.tracker.TrackerSize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

public class HudEditScreen extends Screen {
   private static final int FAKE_COUNT = 6;
   private static final int ANCHOR_BTN = 22;
   private static final int ANCHOR_GAP = 3;
   private static final int CONTROLS_W = 185;
   private static final int ROW_H = 22;
   private static final int CB_SIZE = 14;
   private static final int CB_GAP = 3;
   private int controlsH;
   private static final int ROW_SIZE = 0;
   private static final int ROW_MODE = 1;
   private final Screen previousScreen;
   private final HudAnchor oldAnchor;
   private final int oldOffsetX;
   private final int oldOffsetY;
   private final TrackerSize oldSize;
   private final TrackerDisplayMode oldMode;
   private HudAnchor newAnchor;
   private int newOffsetX;
   private int newOffsetY;
   private TrackerSize newSize;
   private TrackerDisplayMode newMode;
   private boolean isDragging = false;
   private double dragBoxOffsetX;
   private double dragBoxOffsetY;
   private final List<Identifier> fakeIds = new ArrayList<>();
   private int previewBoxH;

   public HudEditScreen(Screen previousScreen) {
      super(Component.translatable("gui.advancements.text.tracker.edit"));
      this.previousScreen = previousScreen;
      this.oldAnchor = ModernAdvancementsClient.CONFIG.boundingBoxAnchor();
      this.oldOffsetX = ModernAdvancementsClient.CONFIG.boundingBoxOffsetX();
      this.oldOffsetY = ModernAdvancementsClient.CONFIG.boundingBoxOffsetY();
      this.oldSize = ModernAdvancementsClient.CONFIG.trackerSize();
      this.oldMode = ModernAdvancementsClient.CONFIG.trackerDisplayMode();
      this.newAnchor = this.oldAnchor;
      this.newOffsetX = this.oldOffsetX;
      this.newOffsetY = this.oldOffsetY;
      this.newSize = this.oldSize;
      this.newMode = this.oldMode;

      for (int i = 0; i < 6; i++) {
         this.fakeIds.add(Identifier.fromNamespaceAndPath("modern-advancements", "fake_" + i));
      }
   }

   private void playClickSound() {
      AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
   }

   private int getPanelX() {
      return this.width / 2 - 92;
   }

   private int getPanelY() {
      return this.height / 2 - this.controlsH / 2;
   }

   private int rowY(int idx) {
      return this.getPanelY() + 26 + idx * 22;
   }

   private int getGridY() {
      return this.rowY(1) + 22 + 6 + 9 + 3;
   }

   private void computeControlsH() {
      int helpMaxW = 175;
      int helpLineCount = 0;

      for (Component c : List.of(Component.translatable("gui.advancements.text.tracker.text_1"), Component.translatable("gui.advancements.text.tracker.text_2"))) {
         helpLineCount += this.font.split(c, helpMaxW).size();
      }

      int gridRelY = this.rowY(1) - this.getPanelY() + 22 + 6 + 9 + 3;
      int gridEnd = gridRelY + 75;
      int helpEnd = gridEnd + 6 + helpLineCount * (9 + 1);
      this.controlsH = helpEnd + 15 + 48;
   }

   protected void init() {
      super.init();
      this.clearWidgets();
      this.previewBoxH = 6 * (TrackedAdvancementsHud.estimateFakeEntryHeight(this.font, this.newMode) + 2);
      this.previewBoxH = Math.min(this.previewBoxH, this.newSize.maxHeight);
      this.computeControlsH();
      int px = this.getPanelX();
      int py = this.getPanelY();
      int bw = 84;
      int btnY = py + this.controlsH - 48;
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.advancements.hud.edit.reset"), var1x -> this.reset()).bounds(px + 6, btnY, bw, 20).build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.advancements.hud.edit.default"), var1x -> this.applyDefault())
            .bounds(px + 6 + bw + 4, btnY, bw, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.advancements.hud.edit.save"), var1x -> this.save()).bounds(px + 6, btnY + 24, bw, 20).build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.advancements.hud.edit.cancel"), var1x -> this.cancel()).bounds(px + 6 + bw + 4, btnY + 24, bw, 20).build()
      );
   }

   private void rebuildPreview() {
      this.previewBoxH = 6 * (TrackedAdvancementsHud.estimateFakeEntryHeight(this.font, this.newMode) + 2);
      this.previewBoxH = Math.min(this.previewBoxH, this.newSize.maxHeight);
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      context.fill(0, 0, this.width, this.height, -1728053248);
      int[] previewOrigin = TrackedAdvancementsHud.computeBoxOrigin(
         this.width, this.height, this.previewBoxH, this.newSize.width, this.newAnchor, this.newOffsetX, this.newOffsetY
      );
      int bx = previewOrigin[0];
      int by = previewOrigin[1];
      TrackedAdvancementsHud.renderEntries(context, this.font, null, this.fakeIds, bx, by, this.previewBoxH, this.newSize.width, this.newMode, true);
      boolean mouseOverPreview = mouseX >= bx && mouseX <= bx + this.newSize.width && mouseY >= by && mouseY <= by + this.previewBoxH;
      context.outline(bx, by, this.newSize.width, this.previewBoxH, mouseOverPreview ? -1 : -7829368);
      this.renderControls(context, mouseX, mouseY);
      super.extractRenderState(context, mouseX, mouseY, delta);
   }

   private void renderControls(GuiGraphicsExtractor context, int mouseX, int mouseY) {
      int px = this.getPanelX();
      int py = this.getPanelY();
      context.fill(px, py, px + 185, py + this.controlsH, -267316975);
      context.outline(px, py, 185, this.controlsH, -8355712);
      context.centeredText(this.font, Component.translatable("gui.advancements.text.tracker.edit"), px + 92, py + 7, -1);
      context.fill(px + 5, py + 19, px + 185 - 5, py + 20, -11513776);
      this.renderLabeledCheckboxRow(
         context, mouseX, mouseY, "gui.advancements.text.tracker.size", new String[]{"small", "medium", "large"}, this.newSize.ordinal(), 0
      );
      this.renderLabeledCheckboxRow(
         context, mouseX, mouseY, "gui.advancements.text.tracker.mode", new String[]{"normal", "compact", "detailed"}, this.newMode.ordinal(), 1
      );
      int anchorLabelY = this.rowY(1) + 22 + 6;
      context.centeredText(this.font, Component.translatable("gui.advancements.text.tracker.anchor_text"), px + 92, anchorLabelY, -7829368);
      int gridTotalW = 72;
      int gridX = px + (185 - gridTotalW) / 2;
      int gridY = this.getGridY();
      HudAnchor[] anchors = HudAnchor.values();

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 3; col++) {
            HudAnchor anchor = anchors[row * 3 + col];
            int ax = gridX + col * 25;
            int ay = gridY + row * 25;
            boolean selected = anchor == this.newAnchor;
            boolean hovered = mouseX >= ax && mouseX <= ax + 22 && mouseY >= ay && mouseY <= ay + 22;
            context.fill(ax, ay, ax + 22, ay + 22, selected ? -16759672 : (hovered ? -13421773 : -14540254));
            context.outline(ax, ay, 22, 22, selected ? -12276993 : (hovered ? -8355712 : -12303292));
            context.centeredText(this.font, anchor.symbol(), ax + 11, ay + (22 - 9) / 2, selected ? -12276993 : -3355444);
         }
      }

      int helpY = gridY + 75 + 6;
      int helpMaxW = 175;

      for (Component helpLine : List.of(
         Component.translatable("gui.advancements.text.tracker.text_1"), Component.translatable("gui.advancements.text.tracker.text_2")
      )) {
         for (FormattedCharSequence wrapped : this.font.split(helpLine, helpMaxW)) {
            context.text(this.font, wrapped, px + 5, helpY, -11184811, false);
            helpY += 9 + 1;
         }
      }
   }

   private void renderLabeledCheckboxRow(
      GuiGraphicsExtractor context, int mouseX, int mouseY, String labelKey, String[] opts, int selectedIdx, int row
   ) {
      int px = this.getPanelX();
      int y = this.rowY(row);
      int midY = y + (22 - 9) / 2;
      context.text(this.font, Component.translatable(labelKey), px + 6, midY, -7829368, false);
      int count = opts.length;
      int totalW = count * 14 + (count - 1) * 3;
      int startX = px + 185 - 6 - totalW;
      int cbY = y + 4;

      for (int i = 0; i < count; i++) {
         int cx = startX + i * 17;
         boolean selected = i == selectedIdx;
         boolean hovered = mouseX >= cx && mouseX <= cx + 14 && mouseY >= cbY && mouseY <= cbY + 14;
         context.fill(cx, cbY, cx + 14, cbY + 14, selected ? -14927002 : (hovered ? -13619152 : -15066598));
         context.outline(cx, cbY, 14, 14, selected ? -12276993 : (hovered ? -9408400 : -12961222));
         if (selected) {
            context.centeredText(this.font, "✔", cx + 7, cbY + (14 - 9) / 2, -7811841);
         }

         if (hovered) {
            context.setTooltipForNextFrame(this.font, Component.translatable("gui.advancements.text.tracker.option." + opts[i]), mouseX, mouseY);
         }
      }
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mouseX = click.x();
      double mouseY = click.y();
      if (click.button() == 0) {
         if (this.tryRowClick(mouseX, mouseY, 0, 3)) {
            return true;
         }

         if (this.tryRowClick(mouseX, mouseY, 1, 3)) {
            return true;
         }

         if (this.handleAnchorGridClick(mouseX, mouseY)) {
            return true;
         }

         int[] previewOrigin = TrackedAdvancementsHud.computeBoxOrigin(
            this.width, this.height, this.previewBoxH, this.newSize.width, this.newAnchor, this.newOffsetX, this.newOffsetY
         );
         int bx = previewOrigin[0];
         int by = previewOrigin[1];
         int px = this.getPanelX();
         int py = this.getPanelY();
         boolean overControls = mouseX >= px && mouseX <= px + 185 && mouseY >= py && mouseY <= py + this.controlsH;
         if (!overControls && mouseX >= bx && mouseX <= bx + this.newSize.width && mouseY >= by && mouseY <= by + this.previewBoxH) {
            this.isDragging = true;
            this.dragBoxOffsetX = mouseX - bx;
            this.dragBoxOffsetY = mouseY - by;
            return true;
         }
      }

      return super.mouseClicked(click, doubled);
   }

   private boolean tryRowClick(double mouseX, double mouseY, int row, int count) {
      int px = this.getPanelX();
      int y = this.rowY(row);
      int totalW = count * 14 + (count - 1) * 3;
      int startX = px + 185 - 6 - totalW;
      int cbY = y + 4;

      for (int i = 0; i < count; i++) {
         int cx = startX + i * 17;
         if (mouseX >= cx && mouseX <= cx + 14 && mouseY >= cbY && mouseY <= cbY + 14) {
            this.playClickSound();
            switch (row) {
               case 0:
                  this.newSize = TrackerSize.values()[i];
                  this.rebuildPreview();
                  this.rebuild();
                  break;
               case 1:
                  this.newMode = TrackerDisplayMode.values()[i];
                  this.rebuildPreview();
                  this.rebuild();
            }

            return true;
         }
      }

      return false;
   }

   private boolean handleAnchorGridClick(double mouseX, double mouseY) {
      HudAnchor[] anchors = HudAnchor.values();
      int gridTotalW = 72;
      int px = this.getPanelX();
      int gridX = px + (185 - gridTotalW) / 2;
      int gridY = this.getGridY();

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 3; col++) {
            int ax = gridX + col * 25;
            int ay = gridY + row * 25;
            if (mouseX >= ax && mouseX <= ax + 22 && mouseY >= ay && mouseY <= ay + 22) {
               this.playClickSound();
               this.newAnchor = anchors[row * 3 + col];
               this.newOffsetX = 0;
               this.newOffsetY = 0;
               return true;
            }
         }
      }

      return false;
   }

   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      if (click.button() == 0 && this.isDragging) {
         double mouseX = click.x();
         double mouseY = click.y();
         double desiredBX = mouseX - this.dragBoxOffsetX;
         double desiredBY = mouseY - this.dragBoxOffsetY;
         this.newOffsetX = (int)(desiredBX - this.newAnchor.getRefX(this.width) + this.newAnchor.getPivotX(this.newSize.width));
         this.newOffsetY = (int)(desiredBY - this.newAnchor.getRefY(this.height) + this.newAnchor.getPivotY(this.previewBoxH));
         return true;
      } else {
         return super.mouseDragged(click, offsetX, offsetY);
      }
   }

   public boolean mouseReleased(MouseButtonEvent click) {
      this.isDragging = false;
      return super.mouseReleased(click);
   }

   public boolean keyPressed(KeyEvent input) {
      if (input.input() == 256) {
         this.cancel();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   private void reset() {
      this.newAnchor = this.oldAnchor;
      this.newOffsetX = this.oldOffsetX;
      this.newOffsetY = this.oldOffsetY;
      this.newSize = this.oldSize;
      this.newMode = this.oldMode;
      this.rebuildPreview();
      this.rebuild();
   }

   private void applyDefault() {
      this.newAnchor = HudAnchor.CENTER_LEFT;
      this.newOffsetX = 0;
      this.newOffsetY = 0;
      this.newSize = TrackerSize.MEDIUM;
      this.newMode = TrackerDisplayMode.NORMAL;
      this.rebuildPreview();
      this.rebuild();
   }

   private void rebuild() {
      this.clearWidgets();
      this.init();
   }

   private void save() {
      ModernAdvancementsClient.CONFIG.boundingBoxAnchor(this.newAnchor);
      ModernAdvancementsClient.CONFIG.boundingBoxOffset(this.newOffsetX, this.newOffsetY);
      ModernAdvancementsClient.CONFIG.trackerSize(this.newSize);
      ModernAdvancementsClient.CONFIG.trackerDisplayMode(this.newMode);
      ModernAdvancementsClient.CONFIG.save();
      HudState.resetAllScroll();
      Minecraft.getInstance().setScreenAndShow(this.previousScreen);
   }

   private void cancel() {
      Minecraft.getInstance().setScreenAndShow(this.previousScreen);
   }
}
