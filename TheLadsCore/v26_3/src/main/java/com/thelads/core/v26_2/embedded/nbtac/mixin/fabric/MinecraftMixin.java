// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.mixin.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.loader.Loader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin
{
	@Inject(method = "<init>", at = @At("RETURN"))
	private void atConstructor(GameConfig gameConfig, CallbackInfo ci)
	{
		new Thread(Loader::load).start();
	}
}
