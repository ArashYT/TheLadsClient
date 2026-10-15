package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pathway 2 (Equip & Dispenser Equip Blocking) + Consolidated FriendlyFire (Pet Damage Protection).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

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
