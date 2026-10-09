// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v1_20_1.perf.dynamicfps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import com.thelads.core.v1_20_1.perf.dynamicfps.Constants;
import com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod;
import com.thelads.core.v1_20_1.perf.dynamicfps.LadsBackgroundBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	@Final
	private Window window;

	@Shadow
	@Final
	public Options options;

	@Unique
	private boolean lads$dynamicFpsStarted;

	/**
	 * Skips the whole world/GUI render for a frame the power state does not want. The loop, ticking, the buffer swap and the
	 * 15 Hz wake-up floor (see FramerateLimitMixin) are untouched, so the game keeps simulating and the window keeps answering.
	 * The first call also starts the engine, which needs the window and the loaded Lads modules.
	 */
	@WrapOperation(
		method = "runTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V"),
		require = 1
	)
	private void lads$renderFrame(GameRenderer renderer, float partialTick, long nanoTime, boolean renderLevel, Operation<Void> original) {
		if (!this.lads$dynamicFpsStarted) {
			this.lads$dynamicFpsStarted = true;
			LadsBackgroundBridge.initialize();
			DynamicFPSMod.init();
			DynamicFPSMod.setWindow(this.window.getWindow());
		}

		if (DynamicFPSMod.checkForRender()) {
			original.call(renderer, partialTick, nanoTime, renderLevel);
		}
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void lads$closeDynamicFps(CallbackInfo callbackInfo) {
		DynamicFPSMod.close();
	}
}
