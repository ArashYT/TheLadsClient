package com.thelads.core.v26_2.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import com.thelads.core.v26_2.feature.LegacySwing;
import com.thelads.core.v26_2.feature.NativeOldAnimations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class LegacySwingMixin {
    @Inject(method="swingArm",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsConsoleSwing(float progress,PoseStack pose,int side,HumanoidArm arm,CallbackInfo ci){
        if(LegacySwing.apply(pose,progress,side))ci.cancel();
    }

    // 1.7 Animations (and Low Shield) at the item draw: the 1.7 placement drawn with no display transform, or no shield
    // while the sword blocks. LegacySwing's turn above stays for items drawn in vanilla's placement.
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit("
        + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"), require = 1)
    private void ladsOldAnimations(ItemStackRenderState state, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline,
                                   Operation<Void> original, @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true) ItemStack item,
                                   @Local(argsOnly = true, ordinal = 0) float partial, @Local(argsOnly = true, ordinal = 2) float swing,
                                   @Local(argsOnly = true, ordinal = 3) float equip) {
        var player = Minecraft.getInstance().player;
        ItemDisplayContext drawn = player == null ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            : NativeOldAnimations.firstPerson(player, hand, item, partial, swing, equip, pose, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
        if (drawn == ItemDisplayContext.NONE) original.call(NativeOldAnimations.firstPersonIcon(player, hand, item), pose, collector, light, overlay, outline);
        else if (drawn != null) original.call(state, pose, collector, light, overlay, outline);
    }
}
