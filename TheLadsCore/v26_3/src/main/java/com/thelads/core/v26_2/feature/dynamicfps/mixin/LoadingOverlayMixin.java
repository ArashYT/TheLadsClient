// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.mixin;

import net.minecraft.client.gui.screens.LoadingOverlay;

import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.thelads.core.v26_2.feature.dynamicfps.util.duck.DuckLoadingOverlay;

@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin implements DuckLoadingOverlay {
	@Shadow
	@Final
	private ReloadInstance reload;

	@Override
	public boolean dynamic_fps$isReloadComplete() {
		return this.reload.isDone();
	}
}
