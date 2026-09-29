// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.client;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v26_2.feature.food.helpers.ExhaustionHelper;
import com.thelads.core.v26_2.feature.food.helpers.FoodHelper;

import java.text.DecimalFormat;

public class DebugInfoHudEntry implements DebugScreenEntry
{
	public static final Identifier ENTRY_ID = Identifier.fromNamespaceAndPath("theladscore", "food_stats");
	public static final Identifier SECTION_ID = Identifier.fromNamespaceAndPath("theladscore", "debug_info");

	private static final DecimalFormat saturationDF = new DecimalFormat("#.##");
	private static final DecimalFormat exhaustionValDF = new DecimalFormat("0.00");
	private static final DecimalFormat exhaustionMaxDF = new DecimalFormat("#.##");

	@Override
	public void display(
		DebugScreenDisplayer lines,
		@Nullable Level world,
		@Nullable LevelChunk clientChunk,
		@Nullable LevelChunk chunk
	)
	{
		if (world != null && com.thelads.core.v26_2.feature.food.FoodOverlayConfig.enabled())
		{
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || mc.player == null)
				return;

			FoodData stats = mc.player.getFoodData();
			if (stats == null)
			{
				return;
			}

			float curExhaustion = ExhaustionHelper.getExhaustion(mc.player);
			float maxExhaustion = FoodHelper.MAX_EXHAUSTION;
			lines.addToGroup(SECTION_ID, "hunger: " + stats.getFoodLevel() + ", sat: " + (com.thelads.core.v26_2.feature.food.network.ClientSyncHandler.saturationSynced ? "" : "~") + saturationDF.format(stats.getSaturationLevel()) + ", exh: " + (com.thelads.core.v26_2.feature.food.network.ClientSyncHandler.exhaustionSynced ? "" : "~") + exhaustionValDF.format(curExhaustion) + "/" + exhaustionMaxDF.format(maxExhaustion));
		}
	}
}
