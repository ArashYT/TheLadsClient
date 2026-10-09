// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.playtime.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v1_20_1.embedded.playtime.util.PlayTimeScanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelStorageSource.class)
public class LevelStorageSourceMixin {
	/**
	 * 1.20.1 has no {@code makeLevelSummary}: the world list reads every world's summary on the background executor in
	 * {@code loadLevelSummaries}. The task of each world is wrapped so the play time and size are read right after the summary,
	 * on the same background thread, and are attached to the {@link LevelSummary} before the list is shown. Only this world list
	 * path is wrapped (not {@code LevelStorageAccess.getSummary}, which runs on the main thread).
	 */
	@WrapOperation(
		method = "loadLevelSummaries",
		at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;")
	)
	private CompletableFuture<LevelSummary> worldplaytimereborn$attachPlayTime(
		Supplier<LevelSummary> summaryReader, Executor executor, Operation<CompletableFuture<LevelSummary>> original,
		@Local LevelStorageSource.LevelDirectory levelDirectory
	) {
		return original.call(PlayTimeScanner.wrap(summaryReader, levelDirectory.path()), executor);
	}
}
