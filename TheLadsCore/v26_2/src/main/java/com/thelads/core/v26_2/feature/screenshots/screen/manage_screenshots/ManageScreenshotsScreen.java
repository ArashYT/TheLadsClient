// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots;

import com.mojang.logging.LogUtils;
import io.github.lgatodu47.catconfig.CatConfig;
import io.github.lgatodu47.catconfigmc.screen.ConfigListener;
import com.thelads.core.v26_2.feature.screenshots.ScreenshotThumbnailManager;
import com.thelads.core.v26_2.feature.screenshots.ScreenshotViewer;
import com.thelads.core.v26_2.feature.screenshots.ScreenshotViewerUtils;
import com.thelads.core.v26_2.feature.screenshots.config.ScreenshotViewerOptions;
import com.thelads.core.v26_2.feature.screenshots.screen.IconButtonWidget;
import com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotViewerConfigScreen;
import com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotViewerTexts;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.components.Button.Plain;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.slf4j.Logger;

public class ManageScreenshotsScreen extends Screen implements ConfigListener, OldParentElementMethods {
   static final CatConfig CONFIG = ScreenshotViewer.getInstance().getConfig();
   static final ScreenshotThumbnailManager THUMBNAILS = ScreenshotViewer.getInstance().getThumbnailManager();
   static final Logger LOGGER = LogUtils.getLogger();
   public static final WidgetSprites DEFAULT_BUTTON_TEXTURES = new WidgetSprites(
      Identifier.withDefaultNamespace("widget/button"),
      Identifier.withDefaultNamespace("widget/button_disabled"),
      Identifier.withDefaultNamespace("widget/button_highlighted")
   );
   private static final Identifier CONFIG_ICON = Identifier.fromNamespaceAndPath("lads_screenshots", "widget/icons/config");
   private static final Identifier REFRESH_ICON = Identifier.fromNamespaceAndPath("lads_screenshots", "widget/icons/refresh");
   private static final Identifier ASCENDING_ORDER_ICON = Identifier.fromNamespaceAndPath("lads_screenshots", "widget/icons/ascending_order");
   private static final Identifier DESCENDING_ORDER_ICON = Identifier.fromNamespaceAndPath("lads_screenshots", "widget/icons/descending_order");
   private static final Identifier OPEN_FOLDER_ICON = Identifier.fromNamespaceAndPath("lads_screenshots", "widget/icons/open_folder");
   private static final Identifier FAST_DELETE_ICON = Identifier.fromNamespaceAndPath("lads_screenshots", "widget/icons/delete");
   private static final Identifier FAST_DELETE_ENABLED_ICON = Identifier.fromNamespaceAndPath("lads_screenshots", "widget/icons/fast_delete_enabled");
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

   public ManageScreenshotsScreen(Screen parent) {
      super(ScreenshotViewerTexts.MANAGE_SCREENSHOTS);
      this.parent = parent;
      this.enlargedScreenshot = new EnlargedScreenshotScreen(this::showScreenshotProperties);
      this.screenshotProperties = new ScreenshotPropertiesMenu(this::client);
      this.enlargeAnimation = (Boolean)CONFIG.getOrFallback(ScreenshotViewerOptions.ENABLE_SCREENSHOT_ENLARGEMENT_ANIMATION, true);
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

   public void tick() {
      if (this.dialogScreen != null) {
         this.dialogScreen.tick();
      }
   }

   protected void init() {
      int spacing = 8;
      int btnHeight = 20;
      this.enlargedScreenshot.init(this.width, this.height);
      int contentWidth = this.width - 24;
      int contentHeight = this.height - 40 - 20;
      if (this.list == null) {
         this.list = new ScreenshotList(this, 12, 24, this.width - 24, this.height - 40 - 20);
         this.list.init();
      } else {
         this.list.updateSize(contentWidth, contentHeight);
         this.list.updateChildren(false);
      }

      this.addWidget(this.list);
      int btnY = this.height - 8 - 20;
      int btnSize = 20;
      int bigBtnWidth = 200;
      this.addRenderableWidget(
         new ManageScreenshotsScreen.ExtendedTexturedButtonWidget(
               2,
               2,
               20,
               20,
               CONFIG_ICON,
               button -> this.minecraft.gui.setScreen(new ScreenshotViewerConfigScreen(this)),
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
            {
               Objects.requireNonNull(ManageScreenshotsScreen.this);
            }

            @Nullable
            @Override
            protected Component getTooltipText() {
               return ManageScreenshotsScreen.this.list == null
                  ? null
                  : (ManageScreenshotsScreen.this.list.isInvertedOrder() ? ScreenshotViewerTexts.DESCENDING_ORDER : ScreenshotViewerTexts.ASCENDING_ORDER);
            }

            @Nullable
            @Override
            public Identifier getIconTexture() {
               return ManageScreenshotsScreen.this.list == null
                  ? null
                  : (
                     ManageScreenshotsScreen.this.list.isInvertedOrder()
                        ? ManageScreenshotsScreen.DESCENDING_ORDER_ICON
                        : ManageScreenshotsScreen.ASCENDING_ORDER_ICON
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
            OPEN_FOLDER_ICON,
            btn -> Util.getPlatform()
               .openFile((File)CONFIG.getOrFallback(ScreenshotViewerOptions.SCREENSHOTS_FOLDER, (java.util.function.Supplier<? extends File>) ScreenshotViewerUtils::getVanillaScreenshotsFolder)),
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
                  if ((Boolean)CONFIG.getOrFallback(ScreenshotViewerOptions.PROMPT_WHEN_DELETING_SCREENSHOT, true)) {
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
                           Component.translatable("screen.lads_screenshots.screenshot_manager.delete_n_screenshots", new Object[]{toDelete.size()}),
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
            {
               Objects.requireNonNull(ManageScreenshotsScreen.this);
            }

            public Component getMessage() {
               List<ScreenshotWidget> toDelete = ManageScreenshotsScreen.this.list.deletionList();
               return (Component)(ManageScreenshotsScreen.this.fastDelete && !toDelete.isEmpty()
                  ? Component.translatable("screen.lads_screenshots.screenshot_manager.delete_n_screenshots", new Object[]{toDelete.size()})
                     .withStyle(ChatFormatting.RED)
                  : super.getMessage());
            }
         }
      );
      this.addRenderableWidget(new ManageScreenshotsScreen.ExtendedTexturedButtonWidget(this.width - 16 - 40, btnY, 20, 20, null, button -> {
         this.fastDelete = !this.fastDelete;
         if (!this.fastDelete) {
            this.list.resetDeleteSelection();
         }
      }, ScreenshotViewerTexts.FAST_DELETE, ScreenshotViewerTexts.FAST_DELETE) {
         {
            Objects.requireNonNull(ManageScreenshotsScreen.this);
         }

         @Override
         public Identifier getIconTexture() {
            return ManageScreenshotsScreen.this.fastDelete ? ManageScreenshotsScreen.FAST_DELETE_ENABLED_ICON : ManageScreenshotsScreen.FAST_DELETE_ICON;
         }
      });
      this.addRenderableWidget(
         new ManageScreenshotsScreen.ExtendedTexturedButtonWidget(
            this.width - 8 - 20, btnY, 20, 20, REFRESH_ICON, button -> this.list.init(), ScreenshotViewerTexts.REFRESH, ScreenshotViewerTexts.REFRESH
         )
      );
      if (this.enlargedScreenshotFile != null) {
         this.list
            .findByFileName(this.enlargedScreenshotFile)
            .ifPresentOrElse(
               this::enlargeScreenshot,
               () -> LOGGER.warn(
                  "Tried to enlarge screenshot with a path '{}' that could not be located in the screenshots folder!",
                  this.enlargedScreenshotFile.getAbsolutePath()
               )
            );
         this.enlargedScreenshotFile = null;
      }
   }

   public void resize(int width, int height) {
      super.resize(width, height);
      this.enlargedScreenshot.resize(width, height);
      if (this.dialogScreen != null) {
         this.dialogScreen.resize(width, height);
      }

      this.screenshotProperties.hide();
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      long now = System.nanoTime();
      float elapsed = Math.clamp((now - animationClock) / 1_000_000_000f, 0, .1f);
      animationClock = now;
      if (this.list != null) {
         this.list
            .render(context, mouseX, mouseY, delta, !this.enlargedScreenshot.renders() && !this.screenshotProperties.renders() && this.dialogScreen == null);
      }

      context.centeredText(this.font, this.title, this.width / 2, 8, 16777215);
      this.renderActionText(context);
      ScreenshotViewerUtils.forEachDrawable(this, drawable -> drawable.extractRenderState(context, mouseX, mouseY, delta));
      Matrix3x2fStack matrices = context.pose();
      if (this.enlargedScreenshot.renders()) {
         float animationTime = 1.0F;
         if (this.enlargeAnimation && minecraft.options.screenEffectScale().get() > 0 && this.screenshotScaleAnimation < 1.0F) {
            animationTime = (float)(1.0 - Math.pow(1.0F - (this.screenshotScaleAnimation = Math.min(1f, this.screenshotScaleAnimation + elapsed * 4f)), 3.0));
         }

         this.enlargedScreenshot.extractBackground(context, mouseX, mouseY, delta);
         matrices.pushMatrix();
         matrices.translate(this.enlargedScreenshot.width / 2.0F * (1.0F - animationTime), this.enlargedScreenshot.height / 2.0F * (1.0F - animationTime));
         matrices.scale(animationTime, animationTime);
         this.enlargedScreenshot.renderImage(context);
         matrices.popMatrix();
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
         this.dialogScreen.extractRenderState(context, mouseX, mouseY, delta);
      } else {
         this.screenshotProperties.extractRenderState(context, mouseX, mouseY, delta);
      }
   }

   private void renderActionText(GuiGraphicsExtractor context) {
      Component text = this.fastDelete ? ScreenshotViewerTexts.FAST_DELETE_MODE : ScreenshotViewerTexts.ZOOM_MODE;
      context.text(this.font, text, this.width - this.font.width(text) - 8, 8, this.fastDelete ? -1359820 : (this.isCtrlDown ? -15147463 : -996830));
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
         this.dialogScreen.init(this.width, this.height);
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.keyPressed(input);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.keyPressed(input);
      } else if (this.enlargedScreenshot.renders()) {
         return this.enlargedScreenshot.keyPressed(input);
      } else {
         this.isCtrlDown = input.key() == 341 || input.key() == 345;
         if (input.key() == 294) {
            this.list.init();
            return true;
         } else {
            return this.list != null && this.list.keyPressed(input) ? true : super.keyPressed(input);
         }
      }
   }

   public boolean keyReleased(KeyEvent input) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.keyReleased(input);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.keyReleased(input);
      } else if (this.enlargedScreenshot.renders()) {
         return this.enlargedScreenshot.keyReleased(input);
      } else {
         if (this.isCtrlDown) {
            this.isCtrlDown = false;
         }

         return super.keyReleased(input);
      }
   }

   public boolean charTyped(CharacterEvent input) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.charTyped(input);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.charTyped(input);
      } else {
         return this.enlargedScreenshot.renders() ? this.enlargedScreenshot.charTyped(input) : super.charTyped(input);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalMovement, double verticalMovement) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.mouseScrolled(mouseX, mouseY, horizontalMovement, verticalMovement);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.mouseScrolled(mouseX, mouseY, horizontalMovement, verticalMovement);
      } else if (this.enlargedScreenshot.renders()) {
         return this.enlargedScreenshot.mouseScrolled(mouseX, mouseY, horizontalMovement, verticalMovement);
      } else if (this.list != null) {
         if (this.isCtrlDown) {
            this.list.updateScreenshotsPerRow(verticalMovement);
            return true;
         } else {
            return this.list.mouseScrolled(mouseX, mouseY, horizontalMovement, verticalMovement);
         }
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalMovement, verticalMovement);
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.mouseClicked(click, doubled);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.mouseClicked(click, doubled);
      } else {
         return this.enlargedScreenshot.renders()
            ? this.enlargedScreenshot.mouseClicked(click, doubled)
            : OldParentElementMethods.super.mouseClicked(click, doubled);
      }
   }

   public boolean mouseReleased(MouseButtonEvent click) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.mouseReleased(click);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.mouseReleased(click);
      } else if (this.enlargedScreenshot.renders()) {
         return this.enlargedScreenshot.mouseReleased(click);
      } else {
         return this.list != null ? this.list.mouseReleased(click) : super.mouseReleased(click);
      }
   }

   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.mouseDragged(click, offsetX, offsetY);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.mouseDragged(click, offsetX, offsetY);
      } else {
         return this.enlargedScreenshot.renders() ? this.enlargedScreenshot.mouseDragged(click, offsetX, offsetY) : super.mouseDragged(click, offsetX, offsetY);
      }
   }

   public Optional<GuiEventListener> getChildAt(double mouseX, double mouseY) {
      if (this.dialogScreen != null) {
         return this.dialogScreen.getChildAt(mouseX, mouseY);
      } else if (this.screenshotProperties.renders()) {
         return this.screenshotProperties.getChildAt(mouseX, mouseY);
      } else {
         return this.enlargedScreenshot.renders() ? this.enlargedScreenshot.getChildAt(mouseX, mouseY) : super.getChildAt(mouseX, mouseY);
      }
   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   public void removed() {
      if (this.list != null) this.list.close();
   }

   public void configUpdated() {
      this.list.onConfigUpdate();
      this.enlargeAnimation = (Boolean)CONFIG.getOrFallback(ScreenshotViewerOptions.ENABLE_SCREENSHOT_ENLARGEMENT_ANIMATION, true);
   }

   public interface CustomHoverState {
      void updateHoveredState(int var1, int var2);
   }

   public static class ExtendedButtonWidget extends Plain implements ManageScreenshotsScreen.CustomHoverState {
      public ExtendedButtonWidget(int x, int y, int width, int height, Component message, OnPress onPress) {
         super(x, y, width, height, message, onPress, Supplier::get);
      }

      public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
         if (this.visible) {
            this.extractWidgetRenderState(context, mouseX, mouseY, delta);
         }
      }

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
         int x, int y, int width, int height, @Nullable Identifier texture, OnPress pressAction, @Nullable Component tooltip, Component text
      ) {
         super(x, y, width, height, text, texture, pressAction);
         this.tooltip = tooltip;
      }

      public ManageScreenshotsScreen.ExtendedTexturedButtonWidget offsetTooltip() {
         this.offsetTooltip = true;
         return this;
      }

      public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
         if (this.visible) {
            this.extractWidgetRenderState(context, mouseX, mouseY, delta);
            this.applyTooltip(context, mouseX, mouseY);
         }
      }

      private void applyTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
         Component tooltipText = this.getTooltipText();
         if (tooltipText != null && this.isHovered()) {
            context.setTooltipForNextFrame(
               Minecraft.getInstance().font, List.of(tooltipText.getVisualOrderText()), this.getTooltipPositioner(), mouseX, mouseY, this.isFocused()
            );
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

      public boolean isHoveredOrFocused() {
         return this.isHovered();
      }

      @Override
      public void updateHoveredState(int mouseX, int mouseY) {
         this.isHovered = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;
      }
   }
}
