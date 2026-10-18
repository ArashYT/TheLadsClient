package com.thelads.smp.features.arise.nb3.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.smp.features.arise.nb3.holdmyitems.HoldMyItemsFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into FirstPersonHandsAndItemsRenderer to angle player hands into a two-handed
 * carry pose when holding bulky blocks or items in first person.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class MixinFirstPersonHandsAndItemsRenderer {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(
            method = "applyItemArmTransform",
            at = @At("TAIL")
    )
    private void ladssmp$applyTwoHandedPose(PoseStack poseStack, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
        if (this.minecraft != null && this.minecraft.player != null) {
            ItemStack held = this.minecraft.player.getMainHandItem();
            if (HoldMyItemsFeature.isBulkyItem(held)) {
                HoldMyItemsFeature.applyTwoHandedTransform(poseStack, arm, equipProgress);
            }
        }
    }
}
