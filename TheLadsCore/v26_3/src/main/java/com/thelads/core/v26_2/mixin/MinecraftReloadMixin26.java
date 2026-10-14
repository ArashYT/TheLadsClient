package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.pack.PackAssetMask;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.GameLoadCookie;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Coordinates the {@link PackAssetMask} lifecycle with {@code Minecraft.reloadResourcePacks}
 * in 26.3 so pack-switch reloads activate their diff mask while F3+T and startup reloads perform a full reload.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftReloadMixin26 {
    @Inject(
        method = "reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;",
        at = @At("HEAD")
    )
    private void ladsBeforeReloadResourcePacks(
            boolean hasError,
            GameLoadCookie cookie,
            CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        PackAssetMask.onReloadStart();
    }

    @Inject(
        method = "reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;",
        at = @At("RETURN")
    )
    private void ladsAfterReloadResourcePacks(
            boolean hasError,
            GameLoadCookie cookie,
            CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        CompletableFuture<Void> future = cir.getReturnValue();
        if (future != null) {
            future.whenComplete((v, t) -> PackAssetMask.endReload());
        } else {
            PackAssetMask.endReload();
        }
    }
}
