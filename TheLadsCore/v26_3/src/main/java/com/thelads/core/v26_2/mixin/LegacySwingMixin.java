package com.thelads.core.v26_2.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import com.thelads.core.v26_2.feature.LegacySwing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class LegacySwingMixin {
    @Inject(method="swingArm",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsConsoleSwing(float progress,PoseStack pose,int side,HumanoidArm arm,CallbackInfo ci){
        if(LegacySwing.apply(pose,progress,side))ci.cancel();
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void ladsLowShield(
            net.minecraft.client.renderer.state.level.PlayerRenderState playerState,
            net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState handsState,
            float partialTicks, float pitch, net.minecraft.world.InteractionHand hand,
            float swingProgress, net.minecraft.world.item.ItemStack item, float equipProgress,
            PoseStack pose,
            net.minecraft.client.renderer.SubmitNodeCollector collector, int light,
            CallbackInfo ci) {
        if (item != null && item.is(net.minecraft.world.item.Items.SHIELD)
                && com.thelads.core.config.ModuleManager.getInstance().getModule("OldAnimations") instanceof com.thelads.core.modules.OldAnimationsModule oam
                && oam.active(com.thelads.core.modules.OldAnimationsModule.Feature.LOW_SHIELD, com.thelads.core.modules.OldAnimationsModule.Platform.MODERN)) {
            pose.translate(0.0f, -0.25f, 0.0f);
        }
    }
}
