// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.playtime.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import com.thelads.core.v26_2.embedded.playtime.config.ServerPlayTimeManager;

public class EventHandlerCommon {
	private static int playTicks;

	/**
	 * Called every client tick to increment current server playtime and periodically flush it to disk.
	 */
	public static void onClientTick() {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();

		if (connection == null) {
			return;
		}

		ServerData serverData = connection.getServerData();

		if (serverData == null) {
			return;
		}

		String connectedServer = serverData.ip;

		ServerPlayTimeManager.onPlayTick(connectedServer);

		if (++playTicks >= 6000) {
			ServerPlayTimeManager.saveAsync();
			playTicks = 0;
		}
	}

	/**
	 * Called when the Minecraft client is stopping to ensure playtime is saved.
	 */
	public static void onLeavingGame() {
		ServerPlayTimeManager.save();
	}

	/**
	 * Called when disconnecting from a server to ensure playtime is saved.
	 */
	public static void onLeaveServer() {
		ServerPlayTimeManager.save();
	}
}
