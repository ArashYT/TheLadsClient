// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.screen;

import io.github.lgatodu47.catconfig.ConfigAccess;
import io.github.lgatodu47.catconfig.ConfigOption;
import com.thelads.core.v26_2.feature.screenshots.ScreenshotViewerUtils;
import com.thelads.core.v26_2.feature.screenshots.config.WidgetPositionOption;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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

   protected void init() {
      super.init();
      this.configuringScreen.init(this.width, this.height);
      com.thelads.core.v26_2.feature.screenshots.ScreenshotViewer.addEntry(this.configuringScreen);
      this.initElementToPlace();
   }

   public void resize(int width, int height) {
      super.resize(width, height);
      this.configuringScreen.resize(width, height);
      this.initElementToPlace();
   }

   private void initElementToPlace() {
      this.previousPosition = null;
      this.elementToPlace = this.remover.removeWidget(this.configuringScreen);
      Optional<WidgetPositionOption.WidgetPosition> optPos = this.config.get(this.option);
      if (this.elementToPlace != null) {
         this.previousPosition = optPos.orElseGet(() -> this.makeWidgetPosition(this.elementToPlace));
      }

      List<AbstractWidget> screenWidgets = Screens.getWidgets(this.configuringScreen);
      this.referenceWidget = screenWidgets.isEmpty() ? null : screenWidgets.getFirst();
      if (optPos.isPresent() && this.previousPosition != null) {
         int x = this.previousPosition.x() + (this.referenceWidget == null ? 0 : this.referenceWidget.getX());
         int y = this.previousPosition.y() + (this.referenceWidget == null ? 0 : this.referenceWidget.getY());
         this.elementToPlace.setPosition(x, y);
      }
   }

   public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      this.configuringScreen.extractRenderState(context, 0, 0, deltaTicks);
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.elementToPlace != null) {
         ScreenshotViewerUtils.renderWidget(this.elementToPlace, context, mouseX, mouseY, delta);
      }

      this.renderTipTexts(context);
   }

   protected void renderTipTexts(GuiGraphicsExtractor context) {
      context.text(this.font, ScreenshotViewerTexts.BUTTON_PLACEMENT_MOVEMENT, 0, 0, -1, false);
      context.textWithWordWrap(this.font, ScreenshotViewerTexts.BUTTON_PLACEMENT_CONFIRM, 0, 10, 250, -15335425, false);
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (this.elementToPlace != null && click.button() == 1) {
         this.elementToPlace.setPosition((int)click.x() - this.elementToPlace.getWidth() / 2, (int)click.y() - this.elementToPlace.getHeight() / 2);
         return true;
      } else {
         return super.mouseClicked(click, doubled);
      }
   }

   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      if (this.elementToPlace != null && click.button() == 1) {
         this.elementToPlace.setPosition((int)click.x() - this.elementToPlace.getWidth() / 2, (int)click.y() - this.elementToPlace.getHeight() / 2);
         return true;
      } else {
         return super.mouseDragged(click, offsetX, offsetY);
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (this.elementToPlace == null) {
         return super.keyPressed(input);
      } else {
         if (input.key() == 257) {
            this.config.put(this.option, this.makeWidgetPosition(this.elementToPlace));
            this.onClose();
            AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
         } else if (input.key() == 67 && this.previousPosition != null) {
            this.elementToPlace.setPosition(this.previousPosition.x() + (referenceWidget == null ? 0 : referenceWidget.getX()), this.previousPosition.y() + (referenceWidget == null ? 0 : referenceWidget.getY()));
            AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
         } else if (input.key() == 82) {
            AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
            WidgetPositionOption.WidgetPosition defaultPos = (WidgetPositionOption.WidgetPosition)this.option.defaultValue();
            if (defaultPos == null) {
               this.config.put(this.option, null);
               this.onClose();
               return true;
            }

            this.elementToPlace.setPosition(defaultPos.x(), defaultPos.y());
         } else if (input.key() == 256) {
            this.onClose();
            AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
         }

         return true;
      }
   }

   private WidgetPositionOption.WidgetPosition makeWidgetPosition(AbstractWidget elementToPlace) {
      return this.referenceWidget == null
         ? new WidgetPositionOption.WidgetPosition(elementToPlace.getX(), elementToPlace.getY())
         : new WidgetPositionOption.WidgetPosition(elementToPlace.getX() - this.referenceWidget.getX(), elementToPlace.getY() - this.referenceWidget.getY());
   }

   public void onClose() {
      super.onClose();
      this.minecraft.gui.setScreen(this.parent);
   }

   @FunctionalInterface
   public interface WidgetRemover {
      @Nullable
      AbstractWidget removeWidget(Screen var1);

      static ConfigureButtonPlacementScreen.WidgetRemover ofIndex(int index) {
         return screen -> {
            try {
               List<AbstractWidget> widgets = Screens.getWidgets(screen);

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
               List<AbstractWidget> widgets = Screens.getWidgets(screen);
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
