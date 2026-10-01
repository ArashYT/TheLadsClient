// SPDX-License-Identifier: MIT
package com.thelads.core.v26_2.feature.screenshots.screen;

import java.io.File;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ClickEvent.Action;

public record ScreenshotChatActionClickEvent(File screenshotFile, ActionType actionType) implements ClickEvent {
   public enum ActionType {
      COPY, OPEN_FILE, OPEN_FOLDER, UPLOAD_IMGUR
   }
   public Action action() {
      return Action.OPEN_URL;
   }
}
