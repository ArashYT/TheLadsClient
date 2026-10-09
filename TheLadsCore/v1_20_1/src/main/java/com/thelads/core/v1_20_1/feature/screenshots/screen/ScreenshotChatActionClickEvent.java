// SPDX-License-Identifier: MIT
package com.thelads.core.v1_20_1.feature.screenshots.screen;

import java.io.File;
import java.util.Objects;
import net.minecraft.network.chat.ClickEvent;

/**
 * The [Copy] / [Open File] / [Open Folder] / [Upload to Imgur] chat buttons. 1.20.1 has a fixed set of click actions, so this
 * is an OPEN_FILE click that carries the screenshot path (what a client without the gallery would do) plus the button that was
 * pressed; the Screen mixin handles it before the vanilla handler runs.
 */
public final class ScreenshotChatActionClickEvent extends ClickEvent {
   public enum ActionType {
      COPY, OPEN_FILE, OPEN_FOLDER, UPLOAD_IMGUR
   }

   private final File screenshotFile;
   private final ActionType actionType;

   public ScreenshotChatActionClickEvent(File screenshotFile, ActionType actionType) {
      super(Action.OPEN_FILE, screenshotFile.getAbsolutePath());
      this.screenshotFile = screenshotFile;
      this.actionType = actionType;
   }

   public File screenshotFile() {
      return screenshotFile;
   }

   public ActionType actionType() {
      return actionType;
   }

   @Override
   public boolean equals(Object other) {
      return other instanceof ScreenshotChatActionClickEvent event && actionType == event.actionType && super.equals(event);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), actionType);
   }
}
