package com.thelads.core.v26_2.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.client.renderer.ItemInHandRenderer;
import com.thelads.core.v26_2.feature.LegacySwing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemInHandRenderer.class)
public class LegacySwingMixin {
    // Legacy Swing: no attack-cooldown dip and no pop after using an item. Switching items still lowers and raises the hand.
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"), require = 1)
    private float ladsNoCooldownDip(float scale) {
        return com.thelads.core.v26_2.feature.NativeQualityOfLife.enabled("LegacySwing") ? 1 : scale;
    }

    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsNoUsePop(net.minecraft.world.InteractionHand hand, CallbackInfo ci) {
        if (com.thelads.core.v26_2.feature.NativeQualityOfLife.enabled("LegacySwing")) ci.cancel();
    }

    @Inject(method="swingArm",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsConsoleSwing(float progress,PoseStack pose,int side,HumanoidArm arm,CallbackInfo ci){
        if(LegacySwing.apply(pose,progress,side))ci.cancel();
    }

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void ladsLowShield(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack item,
                               net.minecraft.world.item.ItemDisplayContext context, com.mojang.blaze3d.vertex.PoseStack pose,
                               net.minecraft.client.renderer.SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (context.firstPerson() && item != null && item.is(net.minecraft.world.item.Items.SHIELD)
                && com.thelads.core.config.ModuleManager.getInstance().getModule("OldAnimations") instanceof com.thelads.core.modules.OldAnimationsModule oam
                && oam.active(com.thelads.core.modules.OldAnimationsModule.Feature.LOW_SHIELD, com.thelads.core.modules.OldAnimationsModule.Platform.MODERN)) {
            pose.translate(0.0f, -0.25f, 0.0f);
        }
    }
}
