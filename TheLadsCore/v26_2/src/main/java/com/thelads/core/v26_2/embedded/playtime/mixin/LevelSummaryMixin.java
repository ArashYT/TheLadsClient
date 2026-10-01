// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.playtime.mixin;

import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.thelads.core.v26_2.embedded.playtime.util.IWithPlayTime;

@Mixin(LevelSummary.class)
public class LevelSummaryMixin implements IWithPlayTime {
	@Unique
	private int worldplaytimereborn$playTimeTicks = -1;

	@Unique
	private long worldplaytimereborn$worldSizeBytes = -1;

	/**
	 * Stores playtime ticks on the level summary for later rendering.
	 */
	@Override
	public void setPlayTimeTicks(int playTimeTicks) {
		this.worldplaytimereborn$playTimeTicks = playTimeTicks;
	}

	/**
	 * Returns cached playtime ticks.
	 */
	@Override
	public int getPlayTimeTicks() {
		return this.worldplaytimereborn$playTimeTicks;
	}

	/**
	 * Stores the world size on the level summary for later rendering.
	 */
	@Override
	public void setWorldSizeBytes(long worldSizeBytes) {
		this.worldplaytimereborn$worldSizeBytes = worldSizeBytes;
	}

	/**
	 * Returns the cached world size.
	 */
	@Override
	public long getWorldSizeBytes() {
		return this.worldplaytimereborn$worldSizeBytes;
	}
}
