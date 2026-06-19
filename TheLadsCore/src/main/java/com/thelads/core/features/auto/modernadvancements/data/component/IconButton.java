package com.thelads.core.features.auto.modernadvancements.data.component;

import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.GuiGraphicsExtractor.HoveredTextEffects;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class IconButton extends AbstractButton {
   public static final int DEFAULT_ICON_TEXT_GAP = 2;
   protected static final IconButton.CreateNarration DEFAULT_NARRATION = Supplier::get;
   private final IconButton.OnPress onPress;
   private final IconButton.CreateNarration createNarration;
   @Nullable
   private Identifier iconSprite;
   private int iconWidth;
   private int iconHeight;
   private IconButton.IconPosition iconPosition;
   private int iconTextGap;

   private IconButton(
      int x,
      int y,
      int width,
      int height,
      Component message,
      IconButton.OnPress onPress,
      IconButton.CreateNarration createNarration,
      @Nullable Identifier iconSprite,
      int iconWidth,
      int iconHeight,
      IconButton.IconPosition iconPosition,
      int iconTextGap
   ) {
      super(x, y, width, height, message);
      this.onPress = onPress;
      this.createNarration = createNarration;
      this.iconSprite = iconSprite;
      this.iconWidth = iconWidth;
      this.iconHeight = iconHeight;
      this.iconPosition = iconPosition;
      this.iconTextGap = iconTextGap;
   }

   public static IconButton.Builder builder(Identifier sprite, IconButton.OnPress onPress) {
      return new IconButton.Builder(Component.empty(), onPress).icon(sprite);
   }

   public static IconButton.Builder builder(Component message, IconButton.OnPress onPress) {
      return new IconButton.Builder(message, onPress);
   }

   public static IconButton.Builder builder(IconButton.OnPress onPress) {
      return new IconButton.Builder(Component.empty(), onPress);
   }

   public void onPress(InputWithModifiers input) {
      this.onPress.onPress(this);
   }

   protected MutableComponent createNarrationMessage() {
      return this.createNarration.createNarrationMessage(() -> super.createNarrationMessage());
   }

   public void updateWidgetNarration(NarrationElementOutput output) {
      this.defaultButtonNarrationText(output);
   }

   protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      this.extractDefaultSprite(graphics);
      boolean hasIcon = this.iconSprite != null;
      boolean hasLabel = !this.getMessage().getString().isEmpty();
      if (hasIcon && !hasLabel) {
         this.renderIconCentred(graphics);
      } else if (hasIcon) {
         switch (this.iconPosition) {
            case LEFT:
               this.renderIconLeft(graphics);
               break;
            case RIGHT:
               this.renderIconRight(graphics);
               break;
            case CENTER:
               this.renderIconCentred(graphics);
               this.extractDefaultLabel(graphics.textRendererForWidget(this, HoveredTextEffects.NONE));
               break;
            case ABOVE:
               this.renderIconAbove(graphics);
         }
      } else {
         this.extractDefaultLabel(graphics.textRendererForWidget(this, HoveredTextEffects.NONE));
      }
   }

   private void renderIconCentred(GuiGraphicsExtractor graphics) {
      int iconX = this.getX() + (this.getWidth() - this.iconWidth) / 2;
      int iconY = this.getY() + (this.getHeight() - this.iconHeight) / 2;
      this.blitIcon(graphics, iconX, iconY);
   }

   private void renderIconLeft(GuiGraphicsExtractor graphics) {
      int iconX = this.getX() + 2;
      int iconY = this.getY() + (this.getHeight() - this.iconHeight) / 2;
      this.blitIcon(graphics, iconX, iconY);
      int textLeft = iconX + this.iconWidth + this.iconTextGap;
      int textRight = this.getRight() - 2;
      int textTop = this.getY();
      int textBot = this.getBottom();
      ActiveTextCollector out = graphics.textRendererForWidget(this, HoveredTextEffects.NONE);
      out.acceptScrollingWithDefaultCenter(this.getMessage(), textLeft, textRight, textTop, textBot);
   }

   private void renderIconRight(GuiGraphicsExtractor graphics) {
      int iconX = this.getRight() - 2 - this.iconWidth;
      int iconY = this.getY() + (this.getHeight() - this.iconHeight) / 2;
      this.blitIcon(graphics, iconX, iconY);
      int textLeft = this.getX() + 2;
      int textRight = iconX - this.iconTextGap;
      int textTop = this.getY();
      int textBot = this.getBottom();
      ActiveTextCollector out = graphics.textRendererForWidget(this, HoveredTextEffects.NONE);
      out.acceptScrollingWithDefaultCenter(this.getMessage(), textLeft, textRight, textTop, textBot);
   }

   private void renderIconAbove(GuiGraphicsExtractor graphics) {
      int totalH = this.iconHeight + this.iconTextGap + 9;
      int blockTop = this.getY() + (this.getHeight() - totalH) / 2;
      int iconX = this.getX() + (this.getWidth() - this.iconWidth) / 2;
      this.blitIcon(graphics, iconX, blockTop);
      int textTop = blockTop + this.iconHeight + this.iconTextGap;
      int textBot = textTop + 9;
      int textLeft = this.getX() + 2;
      int textRight = this.getRight() - 2;
      ActiveTextCollector out = graphics.textRendererForWidget(this, HoveredTextEffects.NONE);
      out.acceptScrollingWithDefaultCenter(this.getMessage(), textLeft, textRight, textTop, textBot);
   }

   private void blitIcon(GuiGraphicsExtractor graphics, int x, int y) {
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Objects.requireNonNull(this.iconSprite), x, y, this.iconWidth, this.iconHeight, ARGB.white(this.alpha));
   }

   public void setIcon(@Nullable Identifier sprite) {
      this.iconSprite = sprite;
   }

   public void setIcon(@Nullable Identifier sprite, int width, int height) {
      this.iconSprite = sprite;
      this.iconWidth = width;
      this.iconHeight = height;
   }

   public void setIconPosition(IconButton.IconPosition position) {
      this.iconPosition = position;
   }

   public void setIconTextGap(int gap) {
      this.iconTextGap = gap;
   }

   @Environment(EnvType.CLIENT)
   public static class Builder {
      private final Component message;
      private final IconButton.OnPress onPress;
      private int x;
      private int y;
      private int width = 150;
      private int height = 20;
      @Nullable
      private Tooltip tooltip;
      private IconButton.CreateNarration createNarration = IconButton.DEFAULT_NARRATION;
      @Nullable
      private Identifier iconSprite;
      private int iconWidth = 0;
      private int iconHeight = 0;
      private IconButton.IconPosition iconPosition = IconButton.IconPosition.LEFT;
      private int iconTextGap = 2;

      public Builder(Component message, IconButton.OnPress onPress) {
         this.message = message;
         this.onPress = onPress;
      }

      public IconButton.Builder pos(int x, int y) {
         this.x = x;
         this.y = y;
         return this;
      }

      public IconButton.Builder width(int width) {
         this.width = width;
         return this;
      }

      public IconButton.Builder size(int width, int height) {
         this.width = width;
         this.height = height;
         return this;
      }

      public IconButton.Builder bounds(int x, int y, int width, int height) {
         return this.pos(x, y).size(width, height);
      }

      public IconButton.Builder icon(Identifier sprite, int width, int height) {
         this.iconSprite = sprite;
         this.iconWidth = width;
         this.iconHeight = height;
         return this;
      }

      public IconButton.Builder icon(Identifier sprite) {
         this.iconSprite = sprite;
         this.iconWidth = this.height - 4;
         this.iconHeight = this.height - 4;
         return this;
      }

      public IconButton.Builder iconPosition(IconButton.IconPosition position) {
         this.iconPosition = position;
         return this;
      }

      public IconButton.Builder iconTextGap(int gap) {
         this.iconTextGap = gap;
         return this;
      }

      public IconButton.Builder tooltip(@Nullable Tooltip tooltip) {
         this.tooltip = tooltip;
         return this;
      }

      public IconButton.Builder createNarration(IconButton.CreateNarration narration) {
         this.createNarration = narration;
         return this;
      }

      public IconButton build() {
         IconButton button = new IconButton(
            this.x,
            this.y,
            this.width,
            this.height,
            this.message,
            this.onPress,
            this.createNarration,
            this.iconSprite,
            this.iconWidth,
            this.iconHeight,
            this.iconPosition,
            this.iconTextGap
         );
         button.setTooltip(this.tooltip);
         return button;
      }
   }

   @FunctionalInterface
   public interface CreateNarration {
      MutableComponent createNarrationMessage(Supplier<MutableComponent> var1);
   }

   @Environment(EnvType.CLIENT)
   public static enum IconPosition {
      LEFT,
      RIGHT,
      CENTER,
      ABOVE;
   }

   @FunctionalInterface
   public interface OnPress {
      void onPress(IconButton var1);
   }
}
