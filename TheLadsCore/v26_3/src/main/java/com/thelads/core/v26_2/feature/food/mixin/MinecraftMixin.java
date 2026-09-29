// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.feature.food.client.HUDOverlayHandler;

@Mixin(Minecraft.class)
public class MinecraftMixin
{
	@Inject(at = @At("HEAD"), method = "tick")
	void onTick(CallbackInfo info)
	{
		com.thelads.core.v26_2.feature.food.FoodOverlayConfig.refresh();
		com.thelads.core.v26_2.feature.food.network.ClientSyncHandler.ensurePlayer(Minecraft.getInstance().player);
		com.thelads.core.v26_2.feature.food.NativeFoodProbe.tick();
		com.thelads.core.v26_2.feature.food.NativeFoodRenderProbe.tick();
		if (HUDOverlayHandler.INSTANCE != null)
			HUDOverlayHandler.INSTANCE.onClientTick();
	}
}
