// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.playtime.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_20_1.embedded.playtime.client.util.PlayTimeRenderer;
import com.thelads.core.v1_20_1.embedded.playtime.config.WptConfig;
import com.thelads.core.v1_20_1.embedded.playtime.util.IWithPlayTime;

@Mixin(WorldSelectionList.WorldListEntry.class)
public class WorldListEntryMixin {
	@Shadow
	@Final
	LevelSummary summary;

	/**
	 * Renders playtime indicator inside the world selection list. 1.20.1 hands the row geometry to {@code render} instead of
	 * having {@code getContentX/Y/Width}; RETURN (not TAIL) because the vanilla method returns early for symlinked worlds.
	 */
	@Inject(at = @At("RETURN"), method = "render")
	public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
		if (!WptConfig.showWorldPlayTime.get() && !WptConfig.showWorldSize.get()) {
			return;
		}

		if (this.summary instanceof IWithPlayTime withPlayTime) {
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
						renderX = left + width - indicatorWidth - 4;
						renderY = top;
					}
					case LEFT -> {
						renderX = left - indicatorWidth - 5;
						renderY = top + 10;
					}
					case RIGHT -> {
						renderX = left + width + 14;
						renderY = top + 10;
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
