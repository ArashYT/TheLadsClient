// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A button with the vanilla button background and a square icon on top. 1.20.1 has no GUI sprite atlas, so an icon is a plain
 * texture (a square of the given size) that is stretched over the widget, like a sprite is on 26.3.
 */
public class IconButtonWidget extends Button {
   /** A square gallery icon. */
   public record Icon(ResourceLocation texture, int size) {
      public static Icon of(String sprite, int size) {
         return new Icon(new ResourceLocation("lads_screenshots", "textures/gui/sprites/" + sprite + ".png"), size);
      }
   }

   public static final Icon SCREENSHOT_VIEWER_ICON = Icon.of("widget/icons/screenshot_viewer", 20);
   public static final Icon CONFIG_ICON = Icon.of("widget/icons/config", 20);
   public static final Icon REFRESH_ICON = Icon.of("widget/icons/refresh", 20);
   public static final Icon ASCENDING_ORDER_ICON = Icon.of("widget/icons/ascending_order", 20);
   public static final Icon DESCENDING_ORDER_ICON = Icon.of("widget/icons/descending_order", 20);
   public static final Icon OPEN_FOLDER_ICON = Icon.of("widget/icons/open_folder", 20);
   public static final Icon DELETE_ICON = Icon.of("widget/icons/delete", 19);
   public static final Icon FAST_DELETE_ENABLED_ICON = Icon.of("widget/icons/fast_delete_enabled", 19);
   public static final Icon COPY_ICON = Icon.of("widget/icons/copy", 19);
   public static final Icon RENAME_ICON = Icon.of("widget/icons/rename", 19);
   public static final Icon CLOSE_ICON = Icon.of("widget/icons/close", 19);

   @Nullable
   private final Icon iconTexture;

   public IconButtonWidget(int x, int y, int width, int height, Component message, @Nullable Icon iconTexture, OnPress pressAction) {
      super(x, y, width, height, message, pressAction, DEFAULT_NARRATION);
      this.iconTexture = iconTexture;
   }

   @Nullable
   public Icon getIconTexture() {
      return this.iconTexture;
   }

   /** The vanilla button background of 1.20.1 (widgets.png): disabled, normal or highlighted. */
   public static void renderVanillaBackground(GuiGraphics context, AbstractWidget widget, boolean highlighted, float alpha) {
      int state = !widget.active ? 0 : highlighted ? 2 : 1;
      context.setColor(1.0F, 1.0F, 1.0F, alpha);
      RenderSystem.enableBlend();
      RenderSystem.enableDepthTest();
      context.blitNineSliced(AbstractWidget.WIDGETS_LOCATION, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), 20, 4, 200, 20, 0, 46 + state * 20);
      context.setColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   /** Stretches an icon over a rectangle. */
   public static void renderIcon(GuiGraphics context, Icon icon, int x, int y, int width, int height, float alpha) {
      context.setColor(1.0F, 1.0F, 1.0F, alpha);
      RenderSystem.enableBlend();
      context.blit(icon.texture(), x, y, width, height, 0.0F, 0.0F, icon.size(), icon.size(), icon.size(), icon.size());
      context.setColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   @Override
   protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
      renderVanillaBackground(context, this, this.isHoveredOrFocused(), this.alpha);
      Icon icon = this.getIconTexture();
      if (icon != null) {
         renderIcon(context, icon, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.alpha);
      }
   }
}
