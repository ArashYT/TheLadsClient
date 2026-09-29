// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.feature.food.network.SyncHandler;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Entity
{
	public ServerPlayerMixin(EntityType<?> entityType, Level world)
	{
		super(entityType, world);
	}

	@Inject(at = @At("HEAD"), method = "tick")
	void onUpdate(CallbackInfo info)
	{
		ServerPlayer player = (ServerPlayer) (Object) this;
		SyncHandler.onPlayerUpdate(player);
	}
}
