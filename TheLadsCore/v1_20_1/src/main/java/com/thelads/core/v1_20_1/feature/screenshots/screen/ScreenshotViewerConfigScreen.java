// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47) and CatConfig-MC (MIT). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.screen;

import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewer1201;
import com.thelads.core.v1_20_1.feature.screenshots.config.ConfigAccess;
import com.thelads.core.v1_20_1.feature.screenshots.config.ConfigListener;
import com.thelads.core.v1_20_1.feature.screenshots.config.ConfigOption;
import com.thelads.core.v1_20_1.feature.screenshots.config.ScreenshotViewerConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

/**
 * The gallery settings: every option with its editor, changes held back until "Save Changes". The parent screen is told
 * (ConfigListener) when this screen closes, whether saved or discarded, as on 26.3.
 */
public class ScreenshotViewerConfigScreen extends Screen {
   private static final int BAR_HEIGHT = 33;
   private final Screen parent;
   private final ScreenshotViewerConfig config;
   private final UnsavedConfig unsavedConfig;
   private final ConfigListener listeners;
   private ConfigOptionList list;

   public ScreenshotViewerConfigScreen(Screen parent) {
      super(ScreenshotViewerTexts.translatable("screen", "config"));
      this.parent = parent;
      this.config = ScreenshotViewer1201.getInstance().getConfig();
      this.unsavedConfig = new UnsavedConfig(this.config);
      this.listeners = ScreenshotViewer1201.getInstance().getThumbnailManager();
   }

   @Override
   protected void init() {
      int buttonsX = (this.width - 308) / 2;
      int buttonsY = this.height - BAR_HEIGHT + (BAR_HEIGHT - 20) / 2;
      this.list = new ConfigOptionList(this.minecraft, this.width, BAR_HEIGHT, this.height - BAR_HEIGHT);
      this.list.addAll(this.unsavedConfig, this.unsavedConfig::changed);
      this.addRenderableWidget(this.list);
      this.addRenderableWidget(Button.builder(ScreenshotViewerTexts.CONFIG_DISCARD.copy().withStyle(ChatFormatting.RED), button -> this.onClose())
         .bounds(buttonsX, buttonsY, 150, 20).build());
      this.addRenderableWidget(Button.builder(ScreenshotViewerTexts.CONFIG_SAVE.copy().withStyle(ChatFormatting.GREEN), button -> this.saveAndClose())
         .bounds(buttonsX + 158, buttonsY, 150, 20).build());
   }

   @Override
   public void tick() {
      super.tick();
      if (this.list != null) this.list.tick();
   }

   @Override
   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      this.renderBackground(context);
      // Behind the rows, and the two separator lines the 26.x header and footer have.
      context.fill(0, BAR_HEIGHT, this.width, this.height - BAR_HEIGHT, 0x99000000);
      context.fill(0, BAR_HEIGHT - 2, this.width, BAR_HEIGHT - 1, 0xFF000000);
      context.fill(0, BAR_HEIGHT - 1, this.width, BAR_HEIGHT, 0xFF373737);
      context.fill(0, this.height - BAR_HEIGHT, this.width, this.height - BAR_HEIGHT + 1, 0xFF000000);
      context.fill(0, this.height - BAR_HEIGHT + 1, this.width, this.height - BAR_HEIGHT + 2, 0xFF373737);
      super.render(context, mouseX, mouseY, delta);
      context.drawCenteredString(this.font, this.title, this.width / 2, (BAR_HEIGHT - 9) / 2, 16777215);
   }

   private void saveAndClose() {
      this.unsavedConfig.saveChanges();
      this.onClose();
   }

   @Override
   public void onClose() {
      this.minecraft.setScreen(this.parent);
      if (this.parent instanceof ConfigListener listener) {
         listener.configUpdated();
      }
   }

   @Override
   public void removed() {
      this.config.writeToFile();
      this.listeners.configUpdated();
   }

   /** Edits go here first; "Save Changes" copies them into the real settings. */
   private static final class UnsavedConfig implements ConfigAccess {
      private final ConfigAccess delegate;
      private final Map<ConfigOption<?>, Object> changes = new HashMap<>();

      UnsavedConfig(ConfigAccess delegate) {
         this.delegate = delegate;
      }

      @Override
      public <T> Optional<T> get(ConfigOption<T> option) {
         return this.changes.containsKey(option) ? Optional.ofNullable(option.type().cast(this.changes.get(option))) : this.delegate.get(option);
      }

      @Override
      public <T> void put(ConfigOption<T> option, T value) {
         this.changes.put(option, value);
      }

      boolean changed(ConfigOption<?> option) {
         return this.changes.containsKey(option) && !Objects.equals(this.changes.get(option), this.delegate.get(option).orElse(null));
      }

      @SuppressWarnings("unchecked")
      void saveChanges() {
         this.changes.forEach((option, value) -> this.delegate.put((ConfigOption<Object>) option, value));
      }
   }
}
