// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewer1201;
import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewerUtils;
import com.thelads.core.v1_20_1.feature.screenshots.config.ConfigAccess;
import com.thelads.core.v1_20_1.feature.screenshots.config.ConfigOption;
import com.thelads.core.v1_20_1.feature.screenshots.config.WidgetPositionOption;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfigureButtonPlacementScreen extends Screen {
   private final Screen parent;
   private final ConfigAccess config;
   private final ConfigOption<WidgetPositionOption.WidgetPosition> option;
   private final Screen configuringScreen;
   private final ConfigureButtonPlacementScreen.WidgetRemover remover;
   @Nullable
   private AbstractWidget referenceWidget;
   @Nullable
   private AbstractWidget elementToPlace;
   @Nullable
   private WidgetPositionOption.WidgetPosition previousPosition;

   public ConfigureButtonPlacementScreen(
      Screen parent,
      ConfigAccess config,
      ConfigOption<WidgetPositionOption.WidgetPosition> option,
      Supplier<Screen> configuringScreenFactory,
      ConfigureButtonPlacementScreen.WidgetRemover remover
   ) {
      super(Component.empty());
      this.parent = parent;
      this.config = config;
      this.option = option;
      this.configuringScreen = configuringScreenFactory.get();
      this.remover = remover;
   }

   @Override
   protected void init() {
      super.init();
      this.configuringScreen.init(this.minecraft, this.width, this.height);
      ScreenshotViewer1201.addEntry(this.configuringScreen);
      this.initElementToPlace();
   }

   @Override
   public void resize(Minecraft minecraft, int width, int height) {
      super.resize(minecraft, width, height);
      this.configuringScreen.resize(minecraft, width, height);
      this.initElementToPlace();
   }

   private void initElementToPlace() {
      this.previousPosition = null;
      this.elementToPlace = this.remover.removeWidget(this.configuringScreen);
      Optional<WidgetPositionOption.WidgetPosition> optPos = this.config.get(this.option);
      if (this.elementToPlace != null) {
         this.previousPosition = optPos.orElseGet(() -> this.makeWidgetPosition(this.elementToPlace));
      }

      List<AbstractWidget> screenWidgets = Screens.getButtons(this.configuringScreen);
      this.referenceWidget = screenWidgets.isEmpty() ? null : screenWidgets.get(0);
      if (optPos.isPresent() && this.previousPosition != null) {
         int x = this.previousPosition.x() + (this.referenceWidget == null ? 0 : this.referenceWidget.getX());
         int y = this.previousPosition.y() + (this.referenceWidget == null ? 0 : this.referenceWidget.getY());
         this.setPosition(this.elementToPlace, x, y);
      }
   }

   private void setPosition(AbstractWidget widget, int x, int y) {
      widget.setX(x);
      widget.setY(y);
   }

   @Override
   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      this.configuringScreen.render(context, 0, 0, delta);
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
      if (this.elementToPlace != null) {
         ScreenshotViewerUtils.renderWidget(this.elementToPlace, context, mouseX, mouseY, delta);
      }

      this.renderTipTexts(context);
   }

   protected void renderTipTexts(GuiGraphics context) {
      context.drawString(this.font, ScreenshotViewerTexts.BUTTON_PLACEMENT_MOVEMENT, 0, 0, -1, false);
      context.drawWordWrap(this.font, ScreenshotViewerTexts.BUTTON_PLACEMENT_CONFIRM, 0, 10, 250, -15335425);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.elementToPlace != null && button == InputConstants.MOUSE_BUTTON_RIGHT) {
         this.setPosition(this.elementToPlace, (int)mouseX - this.elementToPlace.getWidth() / 2, (int)mouseY - this.elementToPlace.getHeight() / 2);
         return true;
      } else {
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double offsetX, double offsetY) {
      if (this.elementToPlace != null && button == InputConstants.MOUSE_BUTTON_RIGHT) {
         this.setPosition(this.elementToPlace, (int)mouseX - this.elementToPlace.getWidth() / 2, (int)mouseY - this.elementToPlace.getHeight() / 2);
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, offsetX, offsetY);
      }
   }

   @Override
   public boolean keyPressed(int key, int scanCode, int modifiers) {
      if (this.elementToPlace == null) {
         return super.keyPressed(key, scanCode, modifiers);
      } else {
         if (key == InputConstants.KEY_RETURN) {
            this.config.put(this.option, this.makeWidgetPosition(this.elementToPlace));
            this.onClose();
            this.elementToPlace.playDownSound(this.minecraft.getSoundManager());
         } else if (key == InputConstants.KEY_C && this.previousPosition != null) {
            this.setPosition(this.elementToPlace, this.previousPosition.x() + (referenceWidget == null ? 0 : referenceWidget.getX()), this.previousPosition.y() + (referenceWidget == null ? 0 : referenceWidget.getY()));
            this.elementToPlace.playDownSound(this.minecraft.getSoundManager());
         } else if (key == InputConstants.KEY_R) {
            this.elementToPlace.playDownSound(this.minecraft.getSoundManager());
            WidgetPositionOption.WidgetPosition defaultPos = this.option.defaultValue();
            if (defaultPos == null) {
               this.config.put(this.option, null);
               this.onClose();
               return true;
            }

            this.setPosition(this.elementToPlace, defaultPos.x(), defaultPos.y());
         } else if (key == InputConstants.KEY_ESCAPE) {
            this.onClose();
            this.elementToPlace.playDownSound(this.minecraft.getSoundManager());
         }

         return true;
      }
   }

   private WidgetPositionOption.WidgetPosition makeWidgetPosition(AbstractWidget elementToPlace) {
      return this.referenceWidget == null
         ? new WidgetPositionOption.WidgetPosition(elementToPlace.getX(), elementToPlace.getY())
         : new WidgetPositionOption.WidgetPosition(elementToPlace.getX() - this.referenceWidget.getX(), elementToPlace.getY() - this.referenceWidget.getY());
   }

   @Override
   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   @FunctionalInterface
   public interface WidgetRemover {
      @Nullable
      AbstractWidget removeWidget(Screen screen);

      static ConfigureButtonPlacementScreen.WidgetRemover ofIndex(int index) {
         return screen -> {
            try {
               List<AbstractWidget> widgets = Screens.getButtons(screen);

               try {
                  return widgets.remove(index);
               } catch (UnsupportedOperationException var4) {
                  return widgets.get(index);
               } catch (Throwable var5) {
                  return null;
               }
            } catch (Throwable var6) {
               return null;
            }
         };
      }

      static ConfigureButtonPlacementScreen.WidgetRemover ofPredicate(@NotNull Predicate<AbstractWidget> widgetPredicate) {
         return screen -> {
            try {
               List<AbstractWidget> widgets = Screens.getButtons(screen);
               AbstractWidget widget = widgets.stream().filter(widgetPredicate).findFirst().orElse(null);
               widgets.remove(widget);
               return widget;
            } catch (Throwable var4) {
               return null;
            }
         };
      }
   }
}
