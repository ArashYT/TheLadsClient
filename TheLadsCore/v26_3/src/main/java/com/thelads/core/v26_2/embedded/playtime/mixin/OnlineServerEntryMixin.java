// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.playtime.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.playtime.client.util.PlayTimeRenderer;
import com.thelads.core.v26_2.embedded.playtime.config.ServerPlayTimeManager;
import com.thelads.core.v26_2.embedded.playtime.config.WptConfig;
import com.thelads.core.v26_2.embedded.playtime.util.ServerEntryRenderPos;

@Mixin(value = ServerSelectionList.OnlineServerEntry.class, priority = 2000)
public class OnlineServerEntryMixin {
	@Shadow
	@Final
	private ServerData serverData;

	@Shadow
	@Final
	private Minecraft minecraft;

	/**
	 * Renders playtime indicator in the multiplayer server list entry.
	 */
	@Inject(at = @At("TAIL"), method = "extractContent")
	public void onRender(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
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

		ServerSelectionList.OnlineServerEntry entry = (ServerSelectionList.OnlineServerEntry) (Object) this;

		switch (renderPos) {
			case AFTER_NAME -> {
				int serverNameStartX = entry.getContentX() + 32 + 3;
				int serverNameWidth = this.minecraft.font.width(serverData.name);
				renderX = serverNameStartX + 3 + serverNameWidth;
				renderY = entry.getContentY() + 1;
			}
			case BEHIND_COUNT -> {
				int statusWidth = this.minecraft.font.width(serverData.status);
				renderX = entry.getContentX() + entry.getContentWidth() - 24 - statusWidth - playTimeWidth;
				renderY = entry.getContentY();
			}
			case LEFT -> {
				renderX = entry.getContentX() - playTimeWidth - 5;
				renderY = entry.getContentY() + 10;
			}
			case RIGHT -> {
				renderX = entry.getContentX() + entry.getContentWidth() + 6;
				renderY = entry.getContentY() + 10;
			}
			default -> {
				return;
			}
		}

		PlayTimeRenderer.render(guiGraphics, renderX, renderY, playTime, WptConfig.serverPlayTimeColor.get());
	}
}
