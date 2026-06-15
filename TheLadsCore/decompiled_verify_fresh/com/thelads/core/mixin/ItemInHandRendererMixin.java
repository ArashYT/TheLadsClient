/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.math.Axis
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.client.renderer.ItemInHandRenderer
 *  net.minecraft.client.renderer.SubmitNodeCollector
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.ItemUseAnimation
 *  org.joml.Quaternionfc
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ZoomModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ItemInHandRenderer.class})
public class ItemInHandRendererMixin {
    @Inject(method={"renderHandsWithItems"}, at={@At(value="HEAD")}, require=0)
    private void ladsHandZoomSetup(float partialTick, PoseStack poseStack, SubmitNodeCollector builder, LocalPlayer player, int light, CallbackInfo ci) {
        Option opt;
        ZoomModule zoom = ZoomModule.getInstance();
        if (zoom == null || !zoom.isEnabled() || !zoom.isActive()) {
            return;
        }
        Module zm = ModuleManager.getInstance().getModule("Zoom");
        Option option = opt = zm != null ? zm.getOption("Hand Zoom") : null;
        if (!(opt instanceof BoolOption) || !((BoolOption)opt).get()) {
            return;
        }
        float f = zoom.getFovMultiplier(partialTick);
        if (f >= 1.0f) {
            return;
        }
        poseStack.translate(0.0f, 0.0f, 0.56f * (1.0f - f));
        poseStack.scale(f, f, f);
    }

    @Inject(method={"renderArmWithItem"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V")}, require=0)
    private void ladsOldAnimations(AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack poseStack, SubmitNodeCollector builder, int light, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("OldAnimations");
        if (m == null || !m.isEnabled()) {
            return;
        }
        if (!player.isUsingItem() || player.getUseItemRemainingTicks() <= 0) {
            return;
        }
        if (player.getUsedItemHand() != hand) {
            return;
        }
        ItemUseAnimation anim = stack.getUseAnimation();
        if (anim == ItemUseAnimation.BLOCK || anim == ItemUseAnimation.BOW || anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK) {
            boolean isMainHand = hand == InteractionHand.MAIN_HAND;
            HumanoidArm armSide = isMainHand ? player.getMainArm() : player.getMainArm().getOpposite();
            int sign = armSide == HumanoidArm.RIGHT ? 1 : -1;
            poseStack.popPose();
            poseStack.pushPose();
            poseStack.translate((float)sign * 0.56f, -0.52f + equipProgress * -0.6f, -0.72f);
            if (anim == ItemUseAnimation.BLOCK) {
                poseStack.translate((float)sign * -0.1414f, 0.08f, 0.1414f);
                poseStack.mulPose((Quaternionfc)Axis.XP.rotationDegrees(-102.25f));
                poseStack.mulPose((Quaternionfc)Axis.YP.rotationDegrees((float)sign * 13.365f));
                poseStack.mulPose((Quaternionfc)Axis.ZP.rotationDegrees((float)sign * 78.05f));
                float sf = (float)Math.sin((double)(swingProgress * swingProgress) * Math.PI);
                float sf1 = (float)Math.sin(Math.sqrt(swingProgress) * Math.PI);
                poseStack.mulPose((Quaternionfc)Axis.YP.rotationDegrees((float)sign * -sf1 * 20.0f));
                poseStack.mulPose((Quaternionfc)Axis.ZP.rotationDegrees((float)sign * -sf * 20.0f));
                poseStack.mulPose((Quaternionfc)Axis.XP.rotationDegrees(-sf * 80.0f));
            } else if (anim == ItemUseAnimation.BOW) {
                poseStack.translate((float)sign * -0.2785682f, 0.18344387f, 0.15731531f);
                poseStack.mulPose((Quaternionfc)Axis.XP.rotationDegrees(-13.935f));
                poseStack.mulPose((Quaternionfc)Axis.YP.rotationDegrees((float)sign * 35.3f));
                poseStack.mulPose((Quaternionfc)Axis.ZP.rotationDegrees((float)sign * -9.785f));
                float useDur = stack.getUseDuration((LivingEntity)player);
                float f8 = useDur - ((float)player.getUseItemRemainingTicks() - partialTicks + 1.0f);
                float f12 = Math.min(f8 / 20.0f, 1.0f);
                f12 = (f12 * f12 + f12 * 2.0f) / 3.0f;
                if (f12 > 0.1f) {
                    float f15 = (float)Math.sin((f8 - 0.1f) * 1.3f);
                    float f18 = f12 - 0.1f;
                    poseStack.translate(0.0f, 0.0f, f15 * f18 * 0.004f);
                }
                poseStack.translate(0.0f, 0.0f, f12 * 0.04f);
                poseStack.scale(1.0f, 1.0f, 1.0f + f12 * 0.2f);
                poseStack.mulPose((Quaternionfc)Axis.YN.rotationDegrees((float)sign * f12 * 40.0f));
            } else {
                float useProgress = ((float)(stack.getUseDuration((LivingEntity)player) - player.getUseItemRemainingTicks()) + partialTicks) / 32.0f;
                float eatBob = (float)(Math.random() * (double)0.002f * (double)useProgress);
                float eatY = (float)(Math.sin((double)useProgress * Math.PI * 40.0) * (double)0.02f * (double)useProgress);
                float eatZ = useProgress * 0.04f;
                poseStack.translate((float)sign * 0.05f + eatBob, -0.02f + eatY, eatZ);
                poseStack.mulPose((Quaternionfc)Axis.XP.rotationDegrees(useProgress * -5.0f));
                poseStack.mulPose((Quaternionfc)Axis.YP.rotationDegrees((float)sign * useProgress * 10.0f));
            }
        }
    }
}

