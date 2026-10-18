package com.thelads.smp.features.arise.nb6.mixin;

import com.thelads.smp.features.arise.nb6.AriseNB6Config;
import com.thelads.smp.features.arise.nb6.FishingMinigameServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to FishingHook to intercept reel actions and trigger the Tide minigame on bite,
 * plus optional Nether lava floating support.
 */
@Mixin(FishingHook.class)
public abstract class MixinFishingHook {

    @Shadow
    private int nibble;

    @Shadow
    public abstract Player getPlayerOwner();

    @Inject(
            method = "retrieve(Lnet/minecraft/world/item/ItemStack;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ladssmp$onRetrieveHook(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        FishingHook self = (FishingHook) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }

        Player owner = this.getPlayerOwner();
        if (owner instanceof ServerPlayer serverPlayer) {
            AriseNB6Config config = AriseNB6Config.get();
            if (!config.enabled) {
                return;
            }

            // If minigame is currently ongoing for this player, ignore extra retrieve clicks
            if (FishingMinigameServer.isMinigameActive(serverPlayer.getUUID())) {
                cir.setReturnValue(0);
                return;
            }

            // On bite, initiate minigame instead of instant reel
            if (this.nibble > 0 && config.doMinigame) {
                boolean started = FishingMinigameServer.startMinigame(serverPlayer, self, stack);
                if (started) {
                    // Prevent default retrieval; hook stays active during minigame
                    cir.setReturnValue(0);
                }
            }
        }
    }

    @Inject(
            method = "tick()V",
            at = @At("TAIL")
    )
    private void ladssmp$onTickLava(CallbackInfo ci) {
        FishingHook self = (FishingHook) (Object) this;
        AriseNB6Config config = AriseNB6Config.get();
        if (config.enabled && config.lavaFishing && self.level().dimension() == Level.NETHER) {
            if (self.isInLava()) {
                self.clearFire();
                Vec3 delta = self.getDeltaMovement();
                // Gentle lava buoyancy
                self.setDeltaMovement(delta.x * 0.9, Math.min(0.04, delta.y * 0.5 + 0.02), delta.z * 0.9);
            }
        }
    }
}
