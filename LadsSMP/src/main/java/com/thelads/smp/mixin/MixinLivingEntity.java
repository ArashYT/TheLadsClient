package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pathway 2 (Equip & Dispenser Equip Blocking) + Consolidated FriendlyFire (Pet Damage Protection)
 * + Consolidated No-Killer-Requirement.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Shadow
    @Nullable
    protected EntityReference<Player> lastHurtByPlayer;

    @Shadow
    protected int lastHurtByPlayerMemoryTime;

    @Shadow
    @Nullable
    public abstract Player getLastHurtByPlayer();

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void ladssmp$creditClosestPlayerOnDeath(ServerLevel serverLevel, DamageSource damageSource, CallbackInfo ci) {
        if (!LadsConfig.get().features.noKillerRequirement) return;
        if (this.getLastHurtByPlayer() == null) {
            LivingEntity self = (LivingEntity) (Object) this;
            Player closest = null;
            double bestDistSq = Double.MAX_VALUE;
            for (ServerPlayer player : serverLevel.players()) {
                if (player != null && !player.isRemoved()) {
                    double distSq = self.distanceToSqr(player);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        closest = player;
                    }
                }
            }
            if (closest != null) {
                this.lastHurtByPlayer = EntityReference.of(closest);
            }
        }
        if (this.lastHurtByPlayerMemoryTime <= 0) {
            this.lastHurtByPlayerMemoryTime = 100;
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
    private void ladssmp$dropNamedEntityNameTag(ServerLevel serverLevel, DamageSource damageSource, CallbackInfo ci) {
        if (!LadsConfig.get().features.nameTagTweaks) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.hasCustomName()) return;
        if (self instanceof Slime slime && slime.getSize() != 4) return;
        if (self instanceof net.minecraft.world.entity.npc.AbstractVillager || self instanceof net.minecraft.world.entity.monster.zombie.ZombieVillager) return;

        ItemStack tagStack = new ItemStack(Items.NAME_TAG);
        tagStack.set(DataComponents.CUSTOM_NAME, self.getCustomName());
        tagStack.set(DataComponents.REPAIR_COST, 0);
        self.spawnAtLocation(serverLevel, tagStack);
    }

    @Inject(method = "isEquippableInSlot", at = @At("HEAD"), cancellable = true)
    private void ladssmp$blockDisabledEquip(ItemStack stack, EquipmentSlot slot, CallbackInfoReturnable<Boolean> cir) {
        if (ItemDisablerSystem.isDisabled(stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canEquipWithDispenser", at = @At("HEAD"), cancellable = true)
    private void ladssmp$blockDisabledDispenserEquip(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ItemDisablerSystem.isDisabled(stack)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Consolidates 'friendlyfire': Protects tamed pets (OwnableEntity) from direct or sweeping/projectile damage
     * dealt by their owner (or other players/pets) unless the attacker is sneaking.
     */
    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void ladssmp$friendlyFirePetProtection(ServerLevel level, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!LadsConfig.get().features.friendlyFireProtection) return;

        LivingEntity victim = (LivingEntity) (Object) this;
        if (!(victim instanceof OwnableEntity ownable) || ownable.getOwnerReference() == null) {
            return;
        }

        Entity attacker = source.getEntity();
        if (attacker == null || attacker.isShiftKeyDown()) {
            return;
        }

        if (attacker instanceof ServerPlayer player) {
            boolean isOwnPet = player.getUUID().equals(ownable.getOwnerReference().getUUID());
            if (isOwnPet || LadsConfig.get().features.protectAllPlayersPets) {
                if (LadsConfig.get().features.showFriendlyFireMessage) {
                    player.sendSystemMessage(Component.literal("[Lads SMP] ")
                            .withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("Pet protection active! Sneak to damage tamed pets.")
                                    .withStyle(ChatFormatting.GRAY)), true);
                }
                cir.setReturnValue(true);
            }
        } else if (attacker instanceof OwnableEntity attackerPet && attackerPet.getOwnerReference() != null) {
            if (attackerPet.getOwnerReference().getUUID().equals(ownable.getOwnerReference().getUUID())) {
                cir.setReturnValue(true);
            }
        }
    }
}
