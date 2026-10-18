package com.thelads.smp.features.arise.nb6.mixin;

import com.thelads.smp.features.arise.nb6.AriseNB6Config;
import com.thelads.smp.features.arise.nb6.FireproofLineItem;
import com.thelads.smp.features.arise.nb6.FishingLootManager;
import com.thelads.smp.features.arise.nb6.FishingMinigameServer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to FishingHook to intercept reel actions and trigger the Tide minigame on bite,
 * plus fireproof line lava fishing support with bite simulation and Nether fish.
 */
@Mixin(FishingHook.class)
public abstract class MixinFishingHook {

    @Shadow
    private int nibble;

    @Shadow
    private int outOfWaterTime;

    @Shadow
    private int timeUntilLured;

    @Shadow
    private int timeUntilHooked;

    @Shadow
    @Final
    private int lureSpeed;

    @Shadow
    @Final
    private static EntityDataAccessor<Boolean> DATA_BITING;

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
            if (this.nibble > 0) {
                if (config.doMinigame) {
                    boolean started = FishingMinigameServer.startMinigame(serverPlayer, self, stack);
                    if (started) {
                        // Prevent default retrieval; hook stays active during minigame
                        cir.setReturnValue(0);
                        return;
                    }
                } else {
                    ItemStack loot = FishingLootManager.rollLoot(self, serverPlayer, stack);
                    FishingMinigameServer.awardLootDirect(serverPlayer, self, loot, stack);
                    self.discard();
                    cir.setReturnValue(1);
                    return;
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
        if (!config.enabled || !config.lavaFishing) {
            return;
        }

        // Only process when submerged in lava
        if (!self.isInLava()) {
            return;
        }

        Player owner = this.getPlayerOwner();
        ItemStack rod = ItemStack.EMPTY;
        if (owner != null) {
            if (owner.getMainHandItem().getItem() instanceof FishingRodItem) {
                rod = owner.getMainHandItem();
            } else if (owner.getOffhandItem().getItem() instanceof FishingRodItem) {
                rod = owner.getOffhandItem();
            }
        }

        // Directive: Bobber survives lava ONLY with fireproof_line upgrade
        if (!FireproofLineItem.isFireproofUpgradePresent(owner, rod)) {
            if (!self.level().isClientSide()) {
                self.level().playSound(null, self.getX(), self.getY(), self.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 1.2f);
                if (self.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            self.getX(), self.getY() + 0.2, self.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
                    serverLevel.sendParticles(ParticleTypes.LAVA,
                            self.getX(), self.getY() + 0.2, self.getZ(), 4, 0.1, 0.1, 0.1, 0.0);
                }
                if (owner instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(
                            Component.literal("§cYour fishing line burned up in the lava! (Requires Fireproof Fishing Line)"), true);
                }
                self.discard();
            }
            return;
        }

        // Fireproof upgrade present: prevent fire damage and apply buoyancy
        self.clearFire();
        Vec3 delta = self.getDeltaMovement();
        self.setDeltaMovement(delta.x * 0.9, Math.min(0.04, delta.y * 0.5 + 0.02), delta.z * 0.9);

        // Keep outOfWaterTime at 0 so vanilla does not treat hook as abandoned/out-of-fluid
        this.outOfWaterTime = 0;

        // Server-side bite sequence in lava
        if (self.level().isClientSide()) {
            return;
        }

        if (owner instanceof ServerPlayer serverPlayer && FishingMinigameServer.isMinigameActive(serverPlayer.getUUID())) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) self.level();
        RandomSource rand = self.level().getRandom();

        if (this.nibble > 0) {
            this.nibble--;
            if (this.nibble <= 0) {
                this.nibble = 0;
                this.timeUntilHooked = 0;
                this.timeUntilLured = 0;
                self.getEntityData().set(DATA_BITING, false);
            }
        } else if (this.timeUntilHooked > 0) {
            this.timeUntilHooked--;
            // Magma fish approach trail: flame particles approaching bobber
            if (rand.nextFloat() < 0.6f) {
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        self.getX() + (rand.nextDouble() - 0.5) * 0.4,
                        self.getY() + 0.05,
                        self.getZ() + (rand.nextDouble() - 0.5) * 0.4,
                        1, 0, 0.02, 0, 0.01);
            }

            if (this.timeUntilHooked <= 0) {
                // Bite triggered!
                this.nibble = Mth.nextInt(rand, 30, 60);
                self.getEntityData().set(DATA_BITING, true);
                self.level().playSound(null, self.getX(), self.getY(), self.getZ(),
                        SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6f, 0.8f + rand.nextFloat() * 0.4f);
                self.setDeltaMovement(self.getDeltaMovement().add(0.0, -0.15, 0.0));
                serverLevel.sendParticles(ParticleTypes.LAVA, self.getX(), self.getY() + 0.2, self.getZ(), 8, 0.2, 0.1, 0.2, 0.1);
                serverLevel.sendParticles(ParticleTypes.FLAME, self.getX(), self.getY() + 0.1, self.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
            }
        } else if (this.timeUntilLured > 0) {
            this.timeUntilLured--;
            if (this.timeUntilLured < 40 && rand.nextFloat() < 0.35f) {
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        self.getX() + (rand.nextDouble() - 0.5) * 0.3,
                        self.getY() + 0.1,
                        self.getZ() + (rand.nextDouble() - 0.5) * 0.3,
                        1, 0, 0.01, 0, 0.01);
            }
            if (this.timeUntilLured <= 0) {
                this.timeUntilHooked = Mth.nextInt(rand, 20, 60);
            }
        } else {
            int lureBonus = this.lureSpeed * 20 * 5;
            int waitTicks = Mth.nextInt(rand, 100, 500) - lureBonus;
            this.timeUntilLured = Math.max(30, waitTicks);
        }
    }
}
