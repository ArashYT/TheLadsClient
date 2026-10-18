package com.thelads.smp.features.arise.nb3.mixin;

import com.thelads.smp.features.arise.nb3.cosmeticarmor.CosmeticArmorFeature;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into AvatarRenderer to override rendered armor items with cosmetic armor pieces
 * or hide armor slots based on per-slot visibility toggles.
 */
@Mixin(AvatarRenderer.class)
public abstract class MixinAvatarRenderer {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL")
    )
    private void ladssmp$applyCosmeticArmor(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (entity instanceof Player player) {
            ItemStack visualHead = CosmeticArmorFeature.getVisualArmor(player, EquipmentSlot.HEAD);
            ItemStack visualChest = CosmeticArmorFeature.getVisualArmor(player, EquipmentSlot.CHEST);
            ItemStack visualLegs = CosmeticArmorFeature.getVisualArmor(player, EquipmentSlot.LEGS);
            ItemStack visualFeet = CosmeticArmorFeature.getVisualArmor(player, EquipmentSlot.FEET);

            state.headEquipment = visualHead;
            state.chestEquipment = visualChest;
            state.legsEquipment = visualLegs;
            state.feetEquipment = visualFeet;
        }
    }
}
