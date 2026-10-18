package com.thelads.smp.features.arise.nb5.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.smp.features.arise.nb5.AriseNB5Config;
import com.thelads.smp.features.arise.nb5.ParkourActionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Procedural animation engine for third-person player models and
 * first-person camera / hand offsets during parkour movements.
 */
public final class ParkourAnimationManager {

    private ParkourAnimationManager() {}

    /**
     * Applies third-person body roll and tilt rotations to the Avatar renderer's PoseStack.
     */
    public static void applyThirdPersonRotation(AvatarRenderState state, PoseStack poseStack) {
        AriseNB5Config config = AriseNB5Config.get();
        if (!config.enableAnimations) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getId() != state.id) return;

        ParkourActionType action = ParkourClientController.getActiveAction();
        int ticks = ParkourClientController.getActionTicks();

        switch (action) {
            case ROLL -> {
                // Smooth 360-degree forward tumble
                float progress = 1.0f - (ticks / 14.0f);
                float angle = progress * 360.0f;
                poseStack.rotateDegrees(Axis.XP, angle);
            }
            case DODGE -> {
                // Evasive side-tilt
                float progress = ticks / 10.0f;
                float tilt = progress * 15.0f;
                poseStack.rotateDegrees(Axis.ZP, tilt);
            }
            case BREAKFALL -> {
                // Absorption compression
                float progress = ticks / 10.0f;
                poseStack.translate(0.0f, -0.2f * progress, 0.0f);
            }
            default -> {}
        }
    }

    /**
     * Poses player model limbs (arms and legs) during ledge grabbing, climbing, vaulting, and sliding.
     */
    public static void applyPlayerModelAnim(PlayerModel model, AvatarRenderState state) {
        AriseNB5Config config = AriseNB5Config.get();
        if (!config.enableAnimations) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getId() != state.id) return;

        ParkourActionType action = ParkourClientController.getActiveAction();
        int ticks = ParkourClientController.getActionTicks();

        switch (action) {
            case LEDGE_HANG -> {
                // Both arms extended straight up grasping the cliff
                model.rightArm.xRot = -3.14159f;
                model.leftArm.xRot = -3.14159f;
                model.rightArm.zRot = 0.08f;
                model.leftArm.zRot = -0.08f;
                model.rightLeg.xRot = 0.15f;
                model.leftLeg.xRot = -0.15f;
            }
            case LEDGE_CLIMB -> {
                // Arms pushing down onto the ledge to hoist body up
                float progress = 1.0f - (ticks / 12.0f);
                model.rightArm.xRot = -1.2f + progress * 0.8f;
                model.leftArm.xRot = -1.2f + progress * 0.8f;
                model.rightLeg.xRot = -0.8f;
                model.leftLeg.xRot = -0.8f;
            }
            case VAULT -> {
                // Arms pushing down, legs tucked
                model.rightArm.xRot = 0.5f;
                model.leftArm.xRot = 0.5f;
                model.rightLeg.xRot = -1.1f;
                model.leftLeg.xRot = -1.1f;
            }
            case SLIDE -> {
                // Legs forward in sliding posture
                model.rightLeg.xRot = -1.4f;
                model.leftLeg.xRot = -1.4f;
                model.rightArm.xRot = -0.4f;
                model.leftArm.xRot = -0.4f;
            }
            case WALL_JUMP -> {
                // Kick off posture
                model.rightLeg.xRot = -0.9f;
                model.leftLeg.xRot = 0.4f;
            }
            default -> {}
        }
    }

    /**
     * Applies subtle camera dips, pitch rotation, and impact dampening in first-person view.
     */
    public static void applyCameraTransform(CameraRenderState state, PoseStack poseStack) {
        AriseNB5Config config = AriseNB5Config.get();
        if (!config.enableAnimations || !state.isFirstPerson) return;

        ParkourActionType action = ParkourClientController.getActiveAction();
        int ticks = ParkourClientController.getActionTicks();

        switch (action) {
            case ROLL -> {
                if (config.enableCameraRotationRoll) {
                    float progress = 1.0f - (ticks / 14.0f);
                    float pitchRoll = (float) Math.sin(progress * Math.PI) * 20.0f;
                    poseStack.rotateDegrees(Axis.XP, pitchRoll);
                }
            }
            case SLIDE -> {
                float dip = (ticks / 15.0f) * 0.35f;
                poseStack.translate(0.0f, -dip, 0.0f);
            }
            case BREAKFALL -> {
                float dip = (ticks / 10.0f) * 0.25f;
                poseStack.translate(0.0f, -dip, 0.0f);
            }
            default -> {}
        }
    }

    /**
     * Applies first-person hands positioning (arms dip on slide/vault, raise on ledge grab).
     */
    public static void applyFirstPersonHandsTransform(PoseStack poseStack) {
        AriseNB5Config config = AriseNB5Config.get();
        if (!config.enableAnimations) return;

        ParkourActionType action = ParkourClientController.getActiveAction();
        switch (action) {
            case SLIDE, VAULT -> poseStack.translate(0.0f, -0.18f, 0.0f);
            case LEDGE_HANG -> poseStack.translate(0.0f, 0.22f, -0.08f);
            default -> {}
        }
    }
}
