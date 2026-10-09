// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.playtime.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_20_1.embedded.playtime.client.util.PlayTimeRenderer;
import com.thelads.core.v1_20_1.embedded.playtime.config.ServerPlayTimeManager;
import com.thelads.core.v1_20_1.embedded.playtime.config.WptConfig;
import com.thelads.core.v1_20_1.embedded.playtime.util.ServerEntryRenderPos;

@Mixin(value = ServerSelectionList.OnlineServerEntry.class, priority = 2000)
public class OnlineServerEntryMixin {
	@Shadow
	@Final
	private ServerData serverData;

	@Shadow
	@Final
	private Minecraft minecraft;

	/**
	 * Renders playtime indicator in the multiplayer server list entry (1.20.1: the row geometry is passed to {@code render}).
	 */
	@Inject(at = @At("RETURN"), method = "render")
	public void onRender(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
		if (!WptConfig.showServerPlayTime.get()) {
			return;
		}

		int playTime = ServerPlayTimeManager.getPlayTime(serverData.ip);
		int playTimeWidth = PlayTimeRenderer.getWholeWidth(playTime);

		if (playTimeWidth <= 0) {
			return;
		}

		int renderX;
		int renderY;
		ServerEntryRenderPos renderPos = WptConfig.serverPlayTimePosition.get();

		switch (renderPos) {
			case AFTER_NAME -> {
				int serverNameStartX = left + 32 + 3;
				int serverNameWidth = this.minecraft.font.width(serverData.name);
				renderX = serverNameStartX + 3 + serverNameWidth;
				renderY = top + 1;
			}
			case BEHIND_COUNT -> {
				int statusWidth = this.minecraft.font.width(serverData.status);
				renderX = left + width - 24 - statusWidth - playTimeWidth;
				renderY = top;
			}
			case LEFT -> {
				renderX = left - playTimeWidth - 5;
				renderY = top + 10;
			}
			case RIGHT -> {
				renderX = left + width + 6;
				renderY = top + 10;
			}
			default -> {
				return;
			}
		}

		PlayTimeRenderer.render(guiGraphics, renderX, renderY, playTime, WptConfig.serverPlayTimeColor.get());
	}
}
