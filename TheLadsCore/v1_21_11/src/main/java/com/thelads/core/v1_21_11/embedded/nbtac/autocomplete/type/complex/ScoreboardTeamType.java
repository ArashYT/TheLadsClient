// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.scores.PlayerTeam;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.suggestions.StringSuggestion;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.PrimitiveType;

public class ScoreboardTeamType extends ComplexType
{
	public static final ScoreboardTeamType INSTANCE = new ScoreboardTeamType();

	public ScoreboardTeamType()
	{
		super(PrimitiveType.STRING);
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) { return; }

		for (PlayerTeam team : connection.scoreboard().getPlayerTeams())
		{
			list.add(new StringSuggestion(team.getName(), "[#scoreboard_team]", ctx.parserType()));
		}
	}
}
