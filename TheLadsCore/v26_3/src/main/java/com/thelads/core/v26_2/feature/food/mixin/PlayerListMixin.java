// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.feature.food.network.SyncHandler;

@Mixin(PlayerList.class)
public class PlayerListMixin
{
	@Inject(at = @At("TAIL"), method = "placeNewPlayer")
	private void onPlayerConnect(Connection conn, ServerPlayer player, CommonListenerCookie clientData, CallbackInfo info)
	{
		SyncHandler.onPlayerLoggedIn(player);
	}
}
