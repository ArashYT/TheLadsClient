// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.screen.manage_screenshots;

import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotFileIO;
import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewerUtils;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotViewerTexts;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;

final class RenameScreenshotScreen extends Screen {
   private final String previousName;
   private final Consumer<String> newNameConsumer;
   private final Runnable closeAction;
   private Button doneBtn;
   private EditBox textField;
   private String draft;

   RenameScreenshotScreen(String previousName, Consumer<String> newNameConsumer, Runnable closeAction) {
      super(ScreenshotViewerTexts.RENAME_PROMPT);
      this.previousName = previousName;
      this.newNameConsumer = newNameConsumer;
      this.closeAction = closeAction;
   }

   @Override
   protected void init() {
      super.init();
      EditBox textField = new EditBox(this.font, (this.width - 150) / 2, (this.height - 20) / 2, 150, 20, ScreenshotViewerTexts.SCREENSHOT_NAME_INPUT);
      this.textField = textField;
      textField.setMaxLength(128);
      textField.setFilter(RenameScreenshotScreen::checkInvalidCharacters);
      this.doneBtn = Button.builder(CommonComponents.GUI_DONE, btn -> {
         this.newNameConsumer.accept(textField.getValue().trim());
         this.closeAction.run();
      }).pos(this.width / 2 - 4 - 150, this.height / 2 + 50).build();
      this.doneBtn.active = false;
      textField.setResponder(s -> { draft = s; this.doneBtn.active = ScreenshotFileIO.validStem(s) && !s.trim().equals(this.previousName); });
      textField.setValue(draft == null ? this.previousName : draft);
      this.addRenderableWidget(textField);
      this.addRenderableWidget(this.doneBtn);
      this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, btn -> this.closeAction.run()).pos(this.width / 2 + 4, this.height / 2 + 50).build());
      this.setInitialFocus(textField);
   }

   @Override
   public void tick() {
      if (this.textField != null) this.textField.tick();
   }

   @Override
   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
      context.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 70, -1);
      ScreenshotViewerUtils.forEachDrawable(this, drawable -> drawable.render(context, mouseX, mouseY, delta));
   }

   @Override
   public boolean keyPressed(int key, int scanCode, int modifiers) {
      if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN && this.doneBtn != null && this.doneBtn.active) {
         this.doneBtn.onPress();
         return true;
      } else {
         return super.keyPressed(key, scanCode, modifiers);
      }
   }

   @Override
   public void onClose() {
      this.closeAction.run();
   }

   private static boolean checkInvalidCharacters(String s) {
      return s.chars().noneMatch(c -> c == 92 || c == 47 || c == 58 || c == 42 || c == 63 || c == 34 || c == 60 || c == 62 || c == 124);
   }
}
