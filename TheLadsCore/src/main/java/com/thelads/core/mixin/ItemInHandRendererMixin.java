package com.thelads.core.mixin;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ZoomModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    // ── Optifine-style hand zoom ─────────────────────────────────────────────

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), require = 0)
    private void ladsHandZoomSetup(float partialTick, PoseStack poseStack,
                                   net.minecraft.client.renderer.SubmitNodeCollector builder,
                                   net.minecraft.client.player.LocalPlayer player, int light,
                                   CallbackInfo ci) {
        ZoomModule zoom = ZoomModule.getInstance();
        if (zoom == null || !zoom.isEnabled() || !zoom.isActive()) return;

        Module zm = ModuleManager.getInstance().getModule("Zoom");
        Option opt = zm != null ? zm.getOption("Hand Zoom") : null;
        if (!(opt instanceof BoolOption) || !((BoolOption) opt).get()) return;

        float f = zoom.getFovMultiplier(partialTick);
        if (f >= 1.0f) return;

        // Scale the hand down proportionally to the zoom level.
        // Translate towards center first, scale, then restore — keeps hand centered.
        poseStack.translate(0f, 0f, 0.56f * (1f - f)); // push hand back proportionally
        poseStack.scale(f, f, f);
    }

    // ── Old Animations (1.8-style blocking, bow, eating) ────────────────────

    @Inject(
        method = "renderArmWithItem",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"),
        require = 0
    )
    private void ladsOldAnimations(AbstractClientPlayer player, float partialTicks, float pitch,
                                   InteractionHand hand, float swingProgress, ItemStack stack,
                                   float equipProgress, PoseStack poseStack,
                                   net.minecraft.client.renderer.SubmitNodeCollector builder, int light,
                                   CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("OldAnimations");
        if (m == null || !m.isEnabled()) return;
        if (!player.isUsingItem() || player.getUseItemRemainingTicks() <= 0) return;
        if (player.getUsedItemHand() != hand) return;

        ItemUseAnimation anim = stack.getUseAnimation();
        if (anim == ItemUseAnimation.BLOCK || anim == ItemUseAnimation.BOW || anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK) {
            boolean isMainHand = hand == InteractionHand.MAIN_HAND;
            net.minecraft.world.entity.HumanoidArm armSide = isMainHand ? player.getMainArm() : player.getMainArm().getOpposite();
            int sign = armSide == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;

            poseStack.popPose();
            poseStack.pushPose();

            poseStack.translate(sign * 0.56f, -0.52f + equipProgress * -0.6f, -0.72f);

            if (anim == ItemUseAnimation.BLOCK) {
                poseStack.translate(sign * -0.1414f, 0.08f, 0.1414f);
                poseStack.mulPose(Axis.XP.rotationDegrees(-102.25f));
                poseStack.mulPose(Axis.YP.rotationDegrees(sign * 13.365f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sign * 78.05f));

                float sf  = (float) Math.sin(swingProgress * swingProgress * Math.PI);
                float sf1 = (float) Math.sin(Math.sqrt(swingProgress) * Math.PI);
                poseStack.mulPose(Axis.YP.rotationDegrees(sign * -sf1 * 20.0f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sign * -sf  * 20.0f));
                poseStack.mulPose(Axis.XP.rotationDegrees(-sf * 80.0f));

            } else if (anim == ItemUseAnimation.BOW) {
                poseStack.translate(sign * -0.2785682f, 0.18344387f, 0.15731531f);
                poseStack.mulPose(Axis.XP.rotationDegrees(-13.935f));
                poseStack.mulPose(Axis.YP.rotationDegrees(sign * 35.3f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sign * -9.785f));

                float useDur = (float) stack.getUseDuration(player);
                float f8  = useDur - (player.getUseItemRemainingTicks() - partialTicks + 1.0f);
                float f12 = Math.min(f8 / 20.0f, 1.0f);
                f12 = (f12 * f12 + f12 * 2.0f) / 3.0f;
                if (f12 > 0.1f) {
                    float f15 = (float) Math.sin((f8 - 0.1f) * 1.3f);
                    float f18 = f12 - 0.1f;
                    poseStack.translate(0.0f, 0.0f, f15 * f18 * 0.004f);
                }
                poseStack.translate(0.0f, 0.0f, f12 * 0.04f);
                poseStack.scale(1.0f, 1.0f, 1.0f + f12 * 0.2f);
                poseStack.mulPose(Axis.YN.rotationDegrees(sign * f12 * 40.0f));

            } else {
                // EAT / DRINK: old-style eating animation (hand moves towards face)
                float useProgress = (stack.getUseDuration(player) - player.getUseItemRemainingTicks() + partialTicks) / 32.0f;
                float eatBob = (float) (Math.random() * 0.002f * useProgress); // slight wobble
                float eatY   = (float) (Math.sin(useProgress * Math.PI * 40.0f) * 0.02f * useProgress);
                float eatZ   = useProgress * 0.04f;

                poseStack.translate(sign * 0.05f + eatBob, -0.02f + eatY, eatZ);
                poseStack.mulPose(Axis.XP.rotationDegrees(useProgress * -5.0f));
                poseStack.mulPose(Axis.YP.rotationDegrees(sign * useProgress * 10.0f));
            }
        }
    }
}
