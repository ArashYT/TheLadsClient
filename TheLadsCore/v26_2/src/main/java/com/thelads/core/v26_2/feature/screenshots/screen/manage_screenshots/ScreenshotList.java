// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots;

import com.thelads.core.v26_2.feature.screenshots.ScreenshotViewerUtils;
import com.thelads.core.v26_2.feature.screenshots.config.ScreenshotListOrder;
import com.thelads.core.v26_2.feature.screenshots.config.ScreenshotViewerOptions;
import com.thelads.core.v26_2.feature.screenshots.config.VisibilityState;
import com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotViewerTexts;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

final class ScreenshotList
   extends AbstractContainerEventHandler
   implements Renderable,
   NarratableEntry,
   ScreenshotImageList,
   ScreenshotWidget.Context,
   OldParentElementMethods {
   private final ManageScreenshotsScreen mainScreen;
   private final Minecraft client;
   private final int x;
   private final int y;
   private final List<ScreenshotWidget> screenshotWidgets = new ArrayList<>();
   private final List<GuiEventListener> elements = new ArrayList<>();
   private final ScreenshotList.Scrollbar scrollbar = new ScreenshotList.Scrollbar();
   private int width;
   private int height;
   private int scrollY;
   private int scrollSpeedFactor;
   private int screenshotsPerRow;
   private int spacing;
   private int childWidth;
   private int childHeight;
   private boolean invertedOrder;
   private boolean invertedScroll;
   private boolean namesHidden;
   private File screenshotsFolder;
   private boolean scrollbarClicked;
   private static final java.util.concurrent.ThreadPoolExecutor SCANNER=new java.util.concurrent.ThreadPoolExecutor(
      1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.LinkedBlockingQueue<>(),task->{
         Thread thread=new Thread(task,"Lads screenshot scan");thread.setDaemon(true);return thread;
      });
   private java.util.concurrent.Future<?> scanTask;
   private volatile int scanRevision;
   private boolean scanning;

   ScreenshotList(ManageScreenshotsScreen mainScreen, int x, int y, int width, int height) {
      this.mainScreen = mainScreen;
      this.client = mainScreen.client();
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.scrollSpeedFactor = (Integer)ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.SCREEN_SCROLL_SPEED, 10);
      this.screenshotsPerRow = (Integer)ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.INITIAL_SCREENSHOT_AMOUNT_PER_ROW, 4);
      this.invertedOrder = ((ScreenshotListOrder)ManageScreenshotsScreen.CONFIG
            .getOrFallback(ScreenshotViewerOptions.DEFAULT_LIST_ORDER, ScreenshotListOrder.ASCENDING))
         .isInverted();
      this.screenshotsFolder = (File)ManageScreenshotsScreen.CONFIG
         .getOrFallback(ScreenshotViewerOptions.SCREENSHOTS_FOLDER, (java.util.function.Supplier<? extends File>) ScreenshotViewerUtils::getVanillaScreenshotsFolder);
      this.invertedScroll = (Boolean)ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.INVERT_ZOOM_DIRECTION, false);
      this.namesHidden = ManageScreenshotsScreen.CONFIG
         .get(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_TEXT_VISIBILITY)
         .filter(VisibilityState.HIDDEN::equals)
         .isPresent();
      this.updateVariables();
   }

   void updateSize(int width, int height) {
      this.width = width;
      this.height = height;
   }

   void onConfigUpdate() {
      this.scrollSpeedFactor = (Integer)ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.SCREEN_SCROLL_SPEED, 10);
      this.screenshotsPerRow = (Integer)ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.INITIAL_SCREENSHOT_AMOUNT_PER_ROW, 4);
      this.invertedScroll = (Boolean)ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.INVERT_ZOOM_DIRECTION, false);
      this.namesHidden = ManageScreenshotsScreen.CONFIG
         .get(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_TEXT_VISIBILITY)
         .filter(VisibilityState.HIDDEN::equals)
         .isPresent();
      File currentScreenshotsFolder = (File)ManageScreenshotsScreen.CONFIG
         .getOrFallback(ScreenshotViewerOptions.SCREENSHOTS_FOLDER, (java.util.function.Supplier<? extends File>) ScreenshotViewerUtils::getVanillaScreenshotsFolder);
      if (!this.screenshotsFolder.equals(currentScreenshotsFolder)) {
         this.screenshotsFolder = currentScreenshotsFolder;
         this.init();
      } else if (this.invertedOrder
         != ((ScreenshotListOrder)ManageScreenshotsScreen.CONFIG.getOrFallback(ScreenshotViewerOptions.DEFAULT_LIST_ORDER, ScreenshotListOrder.ASCENDING))
            .isInverted()) {
         this.invertOrder();
      } else {
         this.updateChildren(true);
      }
   }

   void init() {
      cancelScan();
      int revision=scanRevision;
      if (com.thelads.core.v26_2.feature.GlobalScreenshots.sharedGallery(screenshotsFolder)) {
         scanning=true;
         scanTask=SCANNER.submit(()->{
            try {
               List<File> files=com.thelads.core.v26_2.feature.GlobalScreenshots.scan(()->revision!=scanRevision);
               if(revision!=scanRevision)return;
               client.execute(()->{
                  if(revision!=scanRevision||client.gui.screen()!=mainScreen)return;
                  scanning=false;install(files);mainScreen.openRequestedScreenshot();
               });
            }catch(Throwable error){client.execute(()->{
               if(revision!=scanRevision)return;
               scanning=false;ScreenshotViewerUtils.fileError("Scan screenshots",screenshotsFolder,error);
            });}
         });
      } else install(ScreenshotViewerUtils.getScreenshotFiles(this.screenshotsFolder));
   }

   private void install(List<File> files) {
      this.clearChildren();
      this.scrollY=0;
      if (!files.isEmpty()) {
         Comparator<File> order=Comparator.comparingLong(File::lastModified).thenComparing(File::getAbsolutePath);
         files.sort(this.invertedOrder ? order.reversed() : order);
         this.updateVariables();
         int maxXOff = this.screenshotsPerRow - 1;
         int childX = this.x + this.spacing;
         int childY = this.y + this.spacing;
         int xOff = 0;

         for (File file : files) {
            ScreenshotWidget widget = new ScreenshotWidget(this.mainScreen, childX, childY, this.childWidth, this.childHeight, this, file);
            this.screenshotWidgets.add(widget);
            this.elements.add(widget);
            if (xOff == maxXOff) {
               xOff = 0;
               childX = this.x + this.spacing;
               childY += this.childHeight + this.spacing;
            } else {
               xOff++;
               childX += this.childWidth + this.spacing;
            }
         }
      }

      this.scrollbar.repositionScrollbar(this.x, this.y, this.width, this.height, this.spacing, this.getTotalHeightOfChildren());
   }

   void updateScreenshotsPerRow(double scrollAmount) {
      scrollAmount = this.invertedScroll ? -scrollAmount : scrollAmount;
      if (scrollAmount > 0.0) {
         if (this.screenshotsPerRow < 8) {
            this.screenshotsPerRow = Math.min(8, this.screenshotsPerRow + 1);
         }
      } else if (scrollAmount < 0.0 && this.screenshotsPerRow > 2) {
         this.screenshotsPerRow = Math.max(2, this.screenshotsPerRow - 1);
      }

      this.updateChildren(false);
   }

   void updateChildren(boolean configUpdated) {
      this.scrollY = 0;
      this.updateVariables();
      int maxXOff = this.screenshotsPerRow - 1;
      int childX = this.x + this.spacing;
      int childY = this.y + this.spacing;
      int xOff = 0;

      for (ScreenshotWidget widget : this.screenshotWidgets) {
         widget.setX(childX);
         widget.updateBaseY(childY);
         widget.setWidth(this.childWidth);
         widget.setHeight(this.childHeight);
         if (configUpdated) {
            widget.onConfigUpdate();
         }

         if (xOff == maxXOff) {
            xOff = 0;
            childX = this.x + this.spacing;
            childY += this.childHeight + this.spacing;
         } else {
            xOff++;
            childX += this.childWidth + this.spacing;
         }
      }

      this.scrollbar.repositionScrollbar(this.x, this.y, this.width, this.height, this.spacing, this.getTotalHeightOfChildren());
   }

   List<ScreenshotWidget> deletionList() {
      return this.mainScreen.isFastDeleteToggled() ? this.screenshotWidgets.stream().filter(ScreenshotWidget::isSelectedForDeletion).toList() : List.of();
   }

   void resetDeleteSelection() {
      this.screenshotWidgets.forEach(ScreenshotWidget::deselectForDeletion);
   }

   private void updateVariables() {
      float windowAspect = (float)this.client.getWindow().getScreenWidth() / this.client.getWindow().getScreenHeight();
      int scrollbarWidth = 6;
      int scrollbarSpacing = 2;
      this.spacing = 4;
      this.childWidth = (this.width - (this.screenshotsPerRow + 1) * this.spacing - 6 - 2) / this.screenshotsPerRow;
      this.childHeight = (int)((this.namesHidden ? 1.0 : 1.08) * this.childWidth / windowAspect);
   }

   private void clearChildren() {
      this.close();
      this.screenshotWidgets.clear();
      this.elements.clear();
   }

   public void close() {
      this.screenshotWidgets.forEach(ScreenshotWidget::close);
   }
   void cancelScan(){
      scanRevision++;scanning=false;
      if(scanTask!=null){scanTask.cancel(true);scanTask=null;SCANNER.purge();}
   }
   boolean scanning(){return scanning;}
   String status(){return scanning?"Scanning instance screenshots...":size()+" screenshots | external originals are read-only";}

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
   }

   void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, boolean updateHoverState) {
      context.fill(this.x, this.y, this.x + this.width, this.y + this.height, ARGB.color(178, 0, 0, 0));
      if (this.screenshotWidgets.isEmpty()) {
         context.centeredText(this.client.font, scanning ? net.minecraft.network.chat.Component.literal("Scanning instance screenshots...") : ScreenshotViewerTexts.NO_SCREENSHOTS, (this.x + this.width) / 2, (this.y + this.height + 8) / 2, 16777215);
      }

      for (ScreenshotWidget screenshotWidget : this.screenshotWidgets) {
         screenshotWidget.updateY(this.scrollY);
         int viewportY = this.y + this.spacing;
         int viewportBottom = this.y + this.height - this.spacing;
         screenshotWidget.updateHoverState(mouseX, mouseY, viewportY, viewportBottom, updateHoverState);
         if (screenshotWidget.getY() + screenshotWidget.getHeight() >= this.y && screenshotWidget.getY() <= this.y + this.height) {
            screenshotWidget.render(context, mouseX, mouseY, delta, viewportY, viewportBottom);
         } else if (!mainScreen.isShowing(screenshotWidget)) { screenshotWidget.close(); }
      }

      if (this.canScroll()) {
         this.scrollbar.render(context, mouseX, mouseY, this.scrollY, updateHoverState, this.scrollbarClicked);
      }
   }

   public List<? extends GuiEventListener> children() {
      return this.elements;
   }

   @Override
   public ScreenshotImageHolder getScreenshot(int index) {
      return this.screenshotWidgets.get(index);
   }

   @Override
   public Optional<ScreenshotImageHolder> findByFileName(File file) {
      return this.screenshotWidgets
         .stream()
         .filter(screenshotWidget -> screenshotWidget.getScreenshotFile().equals(file))
         .map(ScreenshotImageHolder.class::cast)
         .findFirst();
   }

   @Override
   public int size() {
      return this.screenshotWidgets.size();
   }

   @Override
   public int screenshotsPerRow() {
      return this.screenshotsPerRow;
   }

   @Override
   public int currentIndex(ScreenshotWidget widget) {
      return this.screenshotWidgets.indexOf(widget);
   }

   @Override
   public void removeEntry(ScreenshotWidget widget) {
      this.screenshotWidgets.remove(widget);
      this.elements.remove(widget);
      this.updateChildren(false);
   }

   void invertOrder() {
      Collections.reverse(this.screenshotWidgets);
      this.invertedOrder = !this.invertedOrder;
      int previousScrollY = this.scrollY;
      this.updateChildren(false);
      this.scrollY = previousScrollY;
   }

   boolean isInvertedOrder() {
      return this.invertedOrder;
   }

   private boolean canScroll() {
      int totalHeightOfTheChildrens = this.getTotalHeightOfChildren();
      int viewHeight = this.height - 2 * this.spacing;
      return totalHeightOfTheChildrens > viewHeight;
   }

   private boolean canScrollDown() {
      int totalHeightOfTheChildrens = this.getTotalHeightOfChildren();
      int viewHeight = this.height - 2 * this.spacing;
      int leftOver = totalHeightOfTheChildrens - viewHeight;
      return this.scrollY < leftOver;
   }

   private int getTotalHeightOfChildren() {
      int rows = Mth.ceil((float)this.screenshotWidgets.size() / this.screenshotsPerRow);
      return rows * this.childHeight + this.spacing * (rows - 1);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (this.canScroll()) {
         int scrollSpeed = Math.abs((int)(this.scrollSpeedFactor * (6.0F / this.screenshotsPerRow) * verticalAmount));
         if (this.scrollY > 0 && verticalAmount > 0.0) {
            this.scrollY = Math.max(0, this.scrollY - scrollSpeed);
         }

         if (this.canScrollDown() && verticalAmount < 0.0) {
            int totalHeightOfTheChildrens = this.getTotalHeightOfChildren();
            int viewHeight = this.height - 2 * this.spacing;
            int leftOver = totalHeightOfTheChildrens - viewHeight;
            this.scrollY = Math.min(leftOver, this.scrollY + scrollSpeed);
         }

         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      this.scrollbarClicked = false;
      if (this.canScroll() && this.scrollbar.mouseClicked(click.x(), click.y(), click.button(), this.scrollY)) {
         this.scrollbarClicked = true;
         return true;
      } else {
         return OldParentElementMethods.super.mouseClicked(click, doubled);
      }
   }

   public boolean mouseReleased(MouseButtonEvent click) {
      this.scrollbarClicked = false;
      return super.mouseReleased(click);
   }

   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      if (this.scrollbarClicked && this.canScroll()) {
         int totalHeightOfTheChildrens = this.getTotalHeightOfChildren();
         int scrollDelta = this.scrollbar.getScrollOffsetDelta(offsetY, totalHeightOfTheChildrens);
         if (this.scrollY > 0 && scrollDelta > 0) {
            this.scrollY = Math.max(0, this.scrollY - scrollDelta);
         }

         if (this.canScrollDown() && scrollDelta < 0) {
            int viewHeight = this.height - 2 * this.spacing;
            int leftOver = totalHeightOfTheChildrens - viewHeight;
            this.scrollY = Math.min(leftOver, this.scrollY - scrollDelta);
         }
      }

      return super.mouseDragged(click, offsetX, offsetY);
   }

   public boolean keyPressed(KeyEvent input) {
      return this.screenshotWidgets.stream().anyMatch(widget -> widget.keyPressed(input));
   }

   public void updateNarration(NarrationElementOutput builder) {
   }

   public NarrationPriority narrationPriority() {
      return NarrationPriority.NONE;
   }

   private static class Scrollbar {
      private final int spacing = 2;
      private final int width = 6;
      private final int trackWidth = 2;
      private int x;
      private int height;
      private int trackX;
      private int trackY;
      private int trackHeight;
      private IntUnaryOperator scrollbarYGetter;

      void repositionScrollbar(int listX, int listY, int listWith, int listHeight, int listSpacing, int totalHeightOfTheChildrens) {
         this.x = listX + listWith - 2 - 6;
         this.trackX = this.x + 2;
         this.trackY = listY + listSpacing;
         this.trackHeight = listHeight - 2 * listSpacing;
         int scrollbarSpacedTrackHeight = this.trackHeight + 4;
         this.scrollbarYGetter = scrollOffset -> Mth.ceil((float)(scrollOffset * scrollbarSpacedTrackHeight) / totalHeightOfTheChildrens) + listY + 2;
         this.height = totalHeightOfTheChildrens<=0 ? this.trackHeight : this.trackHeight * scrollbarSpacedTrackHeight / totalHeightOfTheChildrens;
      }

      void render(GuiGraphicsExtractor context, double mouseX, double mouseY, int scrollOffset, boolean updateHoverState, boolean clicked) {
         int y = this.scrollbarYGetter.applyAsInt(scrollOffset);
         context.fill(this.trackX, this.trackY, this.trackX + 2, this.trackY + this.trackHeight, -1);
         context.fill(this.x, y, this.x + 6, y + this.height, clicked ? -1 : (this.isHovered(mouseX, mouseY, y) && updateHoverState ? -9605779 : -14803426));
      }

      boolean mouseClicked(double mouseX, double mouseY, double button, int scrollOffset) {
         return button == 0.0 && this.isHovered(mouseX, mouseY, this.scrollbarYGetter.applyAsInt(scrollOffset));
      }

      int getScrollOffsetDelta(double scrollbarDelta, double totalHeightOfTheChildrens) {
         int scrollbarSpacedTrackHeight = this.trackHeight + 4;
         return Mth.ceil(-scrollbarDelta * totalHeightOfTheChildrens / scrollbarSpacedTrackHeight);
      }

      private boolean isHovered(double mouseX, double mouseY, int y) {
         return mouseX >= this.x && mouseY >= y && mouseX < this.x + 6 && mouseY < y + this.height;
      }
   }
}
