// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.screen.manage_screenshots;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.thelads.core.shared.ScreenshotSources;
import com.thelads.core.shared.SharedContentPaths;
import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotFileIO;
import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotThumbnailManager;
import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewer1201;
import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewerUtils;
import com.thelads.core.v1_20_1.feature.screenshots.config.ConfigListener;
import com.thelads.core.v1_20_1.feature.screenshots.config.ScreenshotViewerConfig;
import com.thelads.core.v1_20_1.feature.screenshots.config.ScreenshotViewerOptions;
import com.thelads.core.v1_20_1.feature.screenshots.screen.IconButtonWidget;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotViewerConfigScreen;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotViewerTexts;
import com.thelads.core.v1_20_1.gui.NativeFileDialogs1201;
import java.io.File;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ManageScreenshotsScreen extends Screen implements ConfigListener {
   static final ScreenshotViewerConfig CONFIG = ScreenshotViewer1201.getInstance().getConfig();
   static final ScreenshotThumbnailManager THUMBNAILS = ScreenshotViewer1201.getInstance().getThumbnailManager();
   static final Logger LOGGER = LogUtils.getLogger();
   private final Screen parent;
   private final EnlargedScreenshotScreen enlargedScreenshot;
   private final ScreenshotPropertiesMenu screenshotProperties;
   private ScreenshotList list;
   private boolean fastDelete;
   @Nullable
   private Screen dialogScreen;
   @Nullable
   private File enlargedScreenshotFile;
   private boolean enlargeAnimation;
   private float screenshotScaleAnimation;
   private long animationClock = System.nanoTime();
   boolean isShowing(ScreenshotImageHolder image) { return enlargedScreenshot.isShowing(image); }
   private boolean isCtrlDown;
   private int extractedFrames;
   int extractedFrames(){return extractedFrames;}

   public ManageScreenshotsScreen(Screen parent) {
      super(ScreenshotViewerTexts.MANAGE_SCREENSHOTS);
      this.parent = parent;
      this.enlargedScreenshot = new EnlargedScreenshotScreen(this::showScreenshotProperties);
      this.screenshotProperties = new ScreenshotPropertiesMenu(this::client);
      this.enlargeAnimation = CONFIG.getOrFallback(ScreenshotViewerOptions.ENABLE_SCREENSHOT_ENLARGEMENT_ANIMATION, true);
   }

   public ManageScreenshotsScreen(Screen parent, @Nullable File enlargedScreenshotFile) {
      this(parent);
      this.enlargedScreenshotFile = enlargedScreenshotFile;
   }

   Minecraft client() {
      return this.minecraft;
   }

   public boolean isFastDeleteToggled() {
      return this.fastDelete;
   }

   @Override
   public void tick() {
      if (this.dialogScreen != null) {
         this.dialogScreen.tick();
      }
   }

   @Override
   protected void init() {
      var addFolder = Button.builder(Component.literal("Add folder"), button -> {
         button.active = false;
         NativeFileDialogs1201.choose(true, "Add screenshot folder").whenComplete((folder, error) -> minecraft.execute(() -> {
            button.active = true;
            if (error != null) { ScreenshotViewerUtils.fileError("Choose folder", new File("screenshots"), error); return; }
            if (folder == null) return;
            java.util.concurrent.CompletableFuture.runAsync(() -> {
               try { ScreenshotSources.addCustomRoot(SharedContentPaths.root(), folder); }
               catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
            }, ScreenshotFileIO.IMAGE_EXECUTOR).whenComplete((done, failure) -> minecraft.execute(() -> {
               if (failure != null) { ScreenshotViewerUtils.fileError("Add screenshot folder", folder.toFile(), failure); return; }
               CONFIG.put(ScreenshotViewerOptions.SCREENSHOTS_FOLDER, ScreenshotViewerUtils.getVanillaScreenshotsFolder());
               if (minecraft.screen == this) { list.onConfigUpdate(); list.init(); }
            }));
         }));
      }).bounds(width - 90, 2, 88, 20).build();
      addFolder.setTooltip(Tooltip.create(Component.literal("Scan a screenshot folder, instance, or launcher folder. Originals stay in place.")));
      addRenderableWidget(addFolder);
      this.enlargedScreenshot.init(this.minecraft, this.width, this.height);
      int contentWidth = this.width - 24;
      int contentHeight = this.height - 76;
      if (this.list == null) {
         this.list = new ScreenshotList(this, 12, 24, this.width - 24, this.height - 76);
         this.list.init();
      } else {
         this.list.updateSize(contentWidth, contentHeight);
         this.list.relayout();
      }

      this.addWidget(this.list);
      int btnY = this.height - 8 - 20;
      this.addRenderableWidget(
         new ManageScreenshotsScreen.ExtendedTexturedButtonWidget(
               2,
               2,
               20,
               20,
               IconButtonWidget.CONFIG_ICON,
               button -> this.minecraft.setScreen(new ScreenshotViewerConfigScreen(this)),
               ScreenshotViewerTexts.CONFIG,
               ScreenshotViewerTexts.CONFIG
            )
            .offsetTooltip()
      );
      this.addRenderableWidget(
         new ManageScreenshotsScreen.ExtendedTexturedButtonWidget(8, btnY, 20, 20, null, button -> {
            if (this.list != null) {
               this.list.invertOrder();
            }
         }, null, ScreenshotViewerTexts.ORDER) {
            @Nullable
            @Override
            protected Component getTooltipText() {
               return ManageScreenshotsScreen.this.list == null
                  ? null
                  : (ManageScreenshotsScreen.this.list.isInvertedOrder() ? ScreenshotViewerTexts.DESCENDING_ORDER : ScreenshotViewerTexts.ASCENDING_ORDER);
            }

            @Nullable
            @Override
            public IconButtonWidget.Icon getIconTexture() {
               return ManageScreenshotsScreen.this.list == null
                  ? null
                  : (
                     ManageScreenshotsScreen.this.list.isInvertedOrder()
                        ? IconButtonWidget.DESCENDING_ORDER_ICON
                        : IconButtonWidget.ASCENDING_ORDER_ICON
                  );
            }
         }
      );
      this.addRenderableWidget(
         new ManageScreenshotsScreen.ExtendedTexturedButtonWidget(
            36,
            btnY,
            20,
            20,
            IconButtonWidget.OPEN_FOLDER_ICON,
            btn -> ScreenshotViewerUtils.openPath(CONFIG.getOrElseGet(ScreenshotViewerOptions.SCREENSHOTS_FOLDER, ScreenshotViewerUtils::getVanillaScreenshotsFolder)),
            ScreenshotViewerTexts.OPEN_FOLDER,
            ScreenshotViewerTexts.OPEN_FOLDER
         )
      );
      this.addRenderableWidget(
         new ManageScreenshotsScreen.ExtendedButtonWidget(
            (this.width - 200) / 2,
            btnY,
            200,
            20,
            CommonComponents.GUI_DONE,
            button -> {
               List<ScreenshotWidget> toDelete = this.list.deletionList();
               if (this.fastDelete && !toDelete.isEmpty()) {
                  if (CONFIG.getOrFallback(ScreenshotViewerOptions.PROMPT_WHEN_DELETING_SCREENSHOT, true)) {
                     this.setDialogScreen(
                        new ConfirmDeletionScreen(
                           value -> {
                              if (value) {
                                 toDelete.forEach(ScreenshotWidget::deleteScreenshot);
                              } else {
                                 this.list.resetDeleteSelection();
                              }

                              this.setDialogScreen(null);
                           },
                           Component.translatable("screen.lads_screenshots.screenshot_manager.delete_n_screenshots", toDelete.size()),
                           toDelete.size() == 1 ? ScreenshotViewerTexts.DELETE_WARNING_MESSAGE : ScreenshotViewerTexts.DELETE_MULTIPLE_WARNING_MESSAGE
                        )
                     );
                  } else {
                     toDelete.forEach(ScreenshotWidget::deleteScreenshot);
                  }

                  this.fastDelete = false;
               } else {
                  this.onClose();
               }
            }
         ) {
            @Override
            public Component getMessage() {
               List<ScreenshotWidget> toDelete = ManageScreenshotsScreen.this.list.deletionList();
               return ManageScreenshotsScreen.this.fastDelete && !toDelete.isEmpty()
                  ? Component.translatable("screen.lads_screenshots.screenshot_manager.delete_n_screenshots", toDelete.size())
                     .withStyle(ChatFormatting.RED)
                  : super.getMessage();
            }
         }
      );
      this.addRenderableWidget(new ManageScreenshotsScreen.ExtendedTexturedButtonWidget(this.width - 16 - 40, btnY, 20, 20, null, button -> {
         this.fastDelete = !this.fastDelete;
         if (!this.fastDelete) {
            this.list.resetDeleteSelection();
         }
      }, ScreenshotViewerTexts.FAST_DELETE, ScreenshotViewerTexts.FAST_DELETE) {
         @Override
         public IconButtonWidget.Icon getIconTexture() {
            return ManageScreenshotsScreen.this.fastDelete ? IconButtonWidget.FAST_DELETE_ENABLED_ICON : IconButtonWidget.DELETE_ICON;
         }
      });
      this.addRenderableWidget(
         new ManageScreenshotsScreen.ExtendedTexturedButtonWidget(
            this.width - 8 - 20, btnY, 20, 20, IconButtonWidget.REFRESH_ICON, button -> this.list.init(), ScreenshotViewerTexts.REFRESH, ScreenshotViewerTexts.REFRESH
         )
      );
      openRequestedScreenshot();
   }

   void openRequestedScreenshot() {
      if (this.enlargedScreenshotFile != null) {
         Optional<ScreenshotImageHolder> requested = this.list.findByFileName(this.enlargedScreenshotFile);
         if (requested.isEmpty() && this.list.scanning()) return; // a later partial result may still contain it
         requested.ifPresentOrElse(
               this::enlargeScreenshot,
               () -> LOGGER.warn(
                  "Tried to enlarge screenshot with a path '{}' that could not be located in the screenshots folder!",
                  this.enlargedScreenshotFile.getAbsolutePath()
               )
            );
         this.enlargedScreenshotFile = null;
      }
   }

   /** A rescan closed widgets: move the viewer to the live widget for its file (or close it) so a closed widget is never drawn or reloaded. */
   void widgetsReplaced() {
      this.screenshotProperties.hide();
      ScreenshotImageHolder shown = this.enlargedScreenshot.showing();
      if (shown != null && shown.indexInList() < 0) this.enlargeScreenshot(this.list.findByFileName(shown.getScreenshotFile()).orElse(null));
   }

   @Override
   public void resize(Minecraft minecraft, int width, int height) {
      super.resize(minecraft, width, height);
      this.enlargedScreenshot.resize(minecraft, width, height);
      if (this.dialogScreen != null) {
         this.dialogScreen.resize(minecraft, width, height);
      }

      this.screenshotProperties.hide();
   }

   @Override
   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      this.renderBackground(context);
      extractedFrames++;
      long now = System.nanoTime();
      float elapsed = Mth.clamp((now - animationClock) / 1_000_000_000f, 0, .1f);
      animationClock = now;
      if (this.list != null) {
         this.list
            .render(context, mouseX, mouseY, delta, !this.enlargedScreenshot.renders() && !this.screenshotProperties.renders() && this.dialogScreen == null);
      }

      context.drawCenteredString(this.font, this.title, this.width / 2, 8, 16777215);
      if (list != null && !fastDelete && !isCtrlDown) context.drawCenteredString(this.font, list.status(), this.width / 2, this.height - 43, 0xffc5b7bf);
      this.renderActionText(context);
      ScreenshotViewerUtils.forEachDrawable(this, drawable -> drawable.render(context, mouseX, mouseY, delta));
      PoseStack matrices = context.pose();
      if (this.enlargedScreenshot.renders()) {
         float animationTime = 1.0F;
         if (this.enlargeAnimation && minecraft.options.screenEffectScale().get() > 0 && this.screenshotScaleAnimation < 1.0F) {
            animationTime = (float)(1.0 - Math.pow(1.0F - (this.screenshotScaleAnimation = Math.min(1f, this.screenshotScaleAnimation + elapsed * 4f)), 3.0));
         }

         this.enlargedScreenshot.renderBackground(context);
         matrices.pushPose();
         matrices.translate(this.enlargedScreenshot.width / 2.0F * (1.0F - animationTime), this.enlargedScreenshot.height / 2.0F * (1.0F - animationTime), 0.0F);
         matrices.scale(animationTime, animationTime, 1.0F);
         this.enlargedScreenshot.renderImage(context);
         matrices.popPose();
         this.enlargedScreenshot.render(context, mouseX, mouseY, delta, !this.screenshotProperties.renders() && this.dialogScreen == null);
      } else {
         if (this.screenshotScaleAnimation > 0.0F) {
            this.screenshotScaleAnimation = 0.0F;
         }

         if (!this.screenshotProperties.renders() && this.dialogScreen == null) {
            for (GuiEventListener element : this.children()) {
               if (element instanceof ManageScreenshotsScreen.CustomHoverState hover) {
                  hover.updateHoveredState(mouseX, mouseY);
               }
            }
         }
      }

      if (this.dialogScreen != null) {
         this.dialogScreen.render(context, mouseX, mouseY, delta);
      } else {
         this.screenshotProperties.render(context, mouseX, mouseY, delta);
      }
      ScreenshotViewerUtils.flushCustomTooltip();
   }

   private void renderActionText(GuiGraphics context) {
      if (!fastDelete && !isCtrlDown) return;
      Component text = this.fastDelete ? ScreenshotViewerTexts.FAST_DELETE_MODE : ScreenshotViewerTexts.ZOOM_MODE;
      context.drawCenteredString(this.font, text, this.width / 2, this.height - 43, this.fastDelete ? -1359820 : -15147463);
   }

   void enlargeScreenshot(@Nullable ScreenshotImageHolder showing) {
      if (showing == null) {
         this.enlargedScreenshot.onClose();
      }

      this.enlargedScreenshot.show(showing, this.list);
   }

   void showScreenshotProperties(double mouseX, double mouseY, ScreenshotImageHolder widget) {
      if (this.list != null) {
         this.screenshotProperties.show((int)mouseX, (int)mouseY, this.width, this.height, widget);
      }
   }

   void setDialogScreen(Screen screen) {
      this.dialogScreen = screen;
      if (this.dialogScreen != null) {
         this.dialogScreen.init(this.minecraft, this.width, this.height);
      }
   }

   @Override
   public boolean keyPressed(int key, int scanCode, int modifiers) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.keyPressed(key, scanCode, modifiers);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.keyPressed(key, scanCode, modifiers);
      } else if (this.enlargedScreenshot.renders()) {
         return this.enlargedScreenshot.keyPressed(key, scanCode, modifiers);
      } else {
         this.isCtrlDown = key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL;
         if (key == InputConstants.KEY_F5) {
            this.list.init();
            return true;
         } else {
            return this.list != null && this.list.keyPressed(key, scanCode, modifiers) ? true : super.keyPressed(key, scanCode, modifiers);
         }
      }
   }

   @Override
   public boolean keyReleased(int key, int scanCode, int modifiers) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.keyReleased(key, scanCode, modifiers);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.keyReleased(key, scanCode, modifiers);
      } else if (this.enlargedScreenshot.renders()) {
         return this.enlargedScreenshot.keyReleased(key, scanCode, modifiers);
      } else {
         if (this.isCtrlDown) {
            this.isCtrlDown = false;
         }

         return super.keyReleased(key, scanCode, modifiers);
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.charTyped(chr, modifiers);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.charTyped(chr, modifiers);
      } else {
         return this.enlargedScreenshot.renders() ? this.enlargedScreenshot.charTyped(chr, modifiers) : super.charTyped(chr, modifiers);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double verticalMovement) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.mouseScrolled(mouseX, mouseY, verticalMovement);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.mouseScrolled(mouseX, mouseY, verticalMovement);
      } else if (this.enlargedScreenshot.renders()) {
         return this.enlargedScreenshot.mouseScrolled(mouseX, mouseY, verticalMovement);
      } else if (this.list != null) {
         if (this.isCtrlDown) {
            this.list.updateScreenshotsPerRow(verticalMovement);
            return true;
         } else {
            return this.list.mouseScrolled(mouseX, mouseY, verticalMovement);
         }
      } else {
         return super.mouseScrolled(mouseX, mouseY, verticalMovement);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.mouseClicked(mouseX, mouseY, button);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.mouseClicked(mouseX, mouseY, button);
      } else {
         return this.enlargedScreenshot.renders()
            ? this.enlargedScreenshot.mouseClicked(mouseX, mouseY, button)
            : super.mouseClicked(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.mouseReleased(mouseX, mouseY, button);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.mouseReleased(mouseX, mouseY, button);
      } else if (this.enlargedScreenshot.renders()) {
         return this.enlargedScreenshot.mouseReleased(mouseX, mouseY, button);
      } else {
         return this.list != null ? this.list.mouseReleased(mouseX, mouseY, button) : super.mouseReleased(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double offsetX, double offsetY) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.mouseDragged(mouseX, mouseY, button, offsetX, offsetY);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.mouseDragged(mouseX, mouseY, button, offsetX, offsetY);
      } else {
         return this.enlargedScreenshot.renders()
            ? this.enlargedScreenshot.mouseDragged(mouseX, mouseY, button, offsetX, offsetY)
            : super.mouseDragged(mouseX, mouseY, button, offsetX, offsetY);
      }
   }

   @Override
   public Optional<GuiEventListener> getChildAt(double mouseX, double mouseY) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.getChildAt(mouseX, mouseY);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.getChildAt(mouseX, mouseY);
      } else {
         return this.enlargedScreenshot.renders() ? this.enlargedScreenshot.getChildAt(mouseX, mouseY) : super.getChildAt(mouseX, mouseY);
      }
   }

   @Override
   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   @Override
   public void removed() {
      if (this.list != null) {this.list.cancelScan();this.list.close();}
   }

   @Override
   public void configUpdated() {
      this.list.onConfigUpdate();
      this.enlargeAnimation = CONFIG.getOrFallback(ScreenshotViewerOptions.ENABLE_SCREENSHOT_ENLARGEMENT_ANIMATION, true);
   }

   public interface CustomHoverState {
      void updateHoveredState(int mouseX, int mouseY);
   }

   public static class ExtendedButtonWidget extends Button implements ManageScreenshotsScreen.CustomHoverState {
      public ExtendedButtonWidget(int x, int y, int width, int height, Component message, OnPress onPress) {
         super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
      }

      @Override
      public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
         if (this.visible) {
            this.renderWidget(context, mouseX, mouseY, delta);
         }
      }

      @Override
      public boolean isHoveredOrFocused() {
         return this.isHovered();
      }

      @Override
      public void updateHoveredState(int mouseX, int mouseY) {
         this.isHovered = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;
      }
   }

   public static class ExtendedTexturedButtonWidget extends IconButtonWidget implements ManageScreenshotsScreen.CustomHoverState {
      @Nullable
      private final Component tooltip;
      private boolean offsetTooltip;

      public ExtendedTexturedButtonWidget(
         int x, int y, int width, int height, @Nullable IconButtonWidget.Icon texture, OnPress pressAction, @Nullable Component tooltip, Component text
      ) {
         super(x, y, width, height, text, texture, pressAction);
         this.tooltip = tooltip;
      }

      public ManageScreenshotsScreen.ExtendedTexturedButtonWidget offsetTooltip() {
         this.offsetTooltip = true;
         return this;
      }

      @Override
      public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
         if (this.visible) {
            this.renderWidget(context, mouseX, mouseY, delta);
            this.applyTooltip();
         }
      }

      private void applyTooltip() {
         Component tooltipText = this.getTooltipText();
         Screen screen = Minecraft.getInstance().screen;
         if (tooltipText != null && this.isHovered() && screen != null) {
            screen.setTooltipForNextRenderPass(List.of(tooltipText.getVisualOrderText()), this.getTooltipPositioner(), this.isFocused());
         }
      }

      @Nullable
      protected Component getTooltipText() {
         return this.tooltip;
      }

      protected ClientTooltipPositioner getTooltipPositioner() {
         ClientTooltipPositioner positioner = DefaultTooltipPositioner.INSTANCE;
         return this.offsetTooltip
            ? (screen_width, screen_height, x, y, w, h) -> positioner.positionTooltip(screen_width, screen_height, x, y + this.height, w, h)
            : positioner;
      }

      @Override
      public boolean isHoveredOrFocused() {
         return this.isHovered();
      }

      @Override
      public void updateHoveredState(int mouseX, int mouseY) {
         this.isHovered = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;
      }
   }
}
