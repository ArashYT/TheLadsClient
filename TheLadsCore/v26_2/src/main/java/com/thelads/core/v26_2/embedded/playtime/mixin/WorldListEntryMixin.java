// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.playtime.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.playtime.client.util.PlayTimeRenderer;
import com.thelads.core.v26_2.embedded.playtime.config.WptConfig;
import com.thelads.core.v26_2.embedded.playtime.util.IWithPlayTime;

@Mixin(WorldSelectionList.WorldListEntry.class)
public class WorldListEntryMixin {
	@Shadow
	@Final
	LevelSummary summary;

	/**
	 * Renders playtime indicator inside the world selection list.
	 */
	@Inject(at = @At("TAIL"), method = "extractContent")
	public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean pHovering, float pPartialTick, CallbackInfo ci) {
		if (!WptConfig.showWorldPlayTime.get() && !WptConfig.showWorldSize.get()) {
			return;
		}

		if (this.summary instanceof IWithPlayTime withPlayTime) {
			WorldSelectionList.WorldListEntry entry = (WorldSelectionList.WorldListEntry) (Object) this;
			int ticks = WptConfig.showWorldPlayTime.get() ? withPlayTime.getPlayTimeTicks() : -1;
			long bytes = WptConfig.showWorldSize.get() ? withPlayTime.getWorldSizeBytes() : -1;
			int playTimeWidth = PlayTimeRenderer.getWholeWidth(ticks);
			int worldSizeWidth = PlayTimeRenderer.getWorldSizeWidth(bytes);
			int indicatorWidth = Math.max(playTimeWidth, worldSizeWidth);

			if (indicatorWidth != 0) {
				int renderX;
				int renderY;

				switch (WptConfig.worldPlayTimePosition.get()) {
					case TOP_RIGHT -> {
						renderX = entry.getContentX() + entry.getContentWidth() - indicatorWidth - 4;
						renderY = entry.getContentY();
					}
					case LEFT -> {
						renderX = entry.getContentX() - indicatorWidth - 5;
						renderY = entry.getContentY() + 10;
					}
					case RIGHT -> {
						renderX = entry.getContentX() + entry.getContentWidth() + 14;
						renderY = entry.getContentY() + 10;
					}
					default -> {
						return;
					}
				}

				if (playTimeWidth != 0) {
					PlayTimeRenderer.render(guiGraphics, renderX + indicatorWidth - playTimeWidth, renderY, ticks, WptConfig.worldPlayTimeColor.get());
				}
				if (worldSizeWidth != 0) {
					PlayTimeRenderer.renderWorldSize(guiGraphics, renderX + indicatorWidth - worldSizeWidth, renderY + (playTimeWidth == 0 ? 0 : 10), bytes, WptConfig.worldPlayTimeColor.get());
				}
			}
		}
	}
}
