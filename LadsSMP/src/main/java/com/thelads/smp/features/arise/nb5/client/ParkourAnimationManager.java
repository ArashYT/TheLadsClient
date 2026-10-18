package com.thelads.smp.features.arise.nb5.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.smp.features.arise.nb5.AriseNB5Config;
import com.thelads.smp.features.arise.nb5.ParkourActionType;
import com.thelads.smp.features.arise.nb5.network.ParkourAnimationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Procedural animation engine for third-person player models and
 * first-person camera / hand offsets during parkour movements.
 * Synchronizes animations in multiplayer across all tracked players.
 */
public final class ParkourAnimationManager {

    public static final class TrackedAnimation {
        public ParkourActionType action = ParkourActionType.NONE;
        public int totalTicks = 0;
        public int ticks = 0;
        public int prevTicks = 0;
    }

    private static final Map<Integer, TrackedAnimation> TRACKED_ANIMATIONS = new ConcurrentHashMap<>();

    private ParkourAnimationManager() {}

    /**
     * Updates tracked animation data received from server S2C animation broadcast.
     */
    public static void handleAnimationPacket(ParkourAnimationPayload payload) {
        ParkourActionType action = payload.getActionType();
        if (action == ParkourActionType.NONE) {
            TRACKED_ANIMATIONS.remove(payload.entityId());
        } else {
            TrackedAnimation anim = TRACKED_ANIMATIONS.computeIfAbsent(payload.entityId(), k -> new TrackedAnimation());
            anim.action = action;
            anim.totalTicks = payload.actionTicks();
            anim.ticks = payload.actionTicks();
            anim.prevTicks = payload.actionTicks();
        }
    }

    /**
     * Ticks tracked animations on client end of client tick.
     */
    public static void clientTick() {
        for (Iterator<Map.Entry<Integer, TrackedAnimation>> it = TRACKED_ANIMATIONS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, TrackedAnimation> entry = it.next();
            TrackedAnimation anim = entry.getValue();
            anim.prevTicks = anim.ticks;
            if (anim.ticks > 0) {
                anim.ticks--;
                if (anim.ticks == 0 && anim.action != ParkourActionType.LEDGE_HANG && anim.action != ParkourActionType.CRAWL) {
                    it.remove();
                }
            }
        }
    }

    private static TrackedAnimation getAnimationForState(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == state.id) {
            ParkourActionType localAction = ParkourClientController.getActiveAction();
            if (localAction == ParkourActionType.NONE) return null;
            TrackedAnimation anim = new TrackedAnimation();
            anim.action = localAction;
            anim.ticks = ParkourClientController.getActionTicks();
            anim.totalTicks = getTotalTicksForAction(localAction);
            return anim;
        }
        return TRACKED_ANIMATIONS.get(state.id);
    }

    private static int getTotalTicksForAction(ParkourActionType action) {
        return switch (action) {
            case ROLL -> 14;
            case DODGE, WALL_JUMP, BREAKFALL -> 10;
            case VAULT, LEDGE_CLIMB -> 12;
            case SLIDE -> AriseNB5Config.get().slidingContinuableTick;
            case LEDGE_HANG -> 200;
            case CRAWL -> 20;
            case NONE -> 0;
        };
    }

    /**
     * Applies third-person body roll and tilt rotations to the Avatar renderer's PoseStack.
     * Interpolates with partial ticks and centers rotation pivot point at player waist (+0.9 Y)
     * so third-person rolls do not invert beneath the ground.
     */
    public static void applyThirdPersonRotation(AvatarRenderState state, PoseStack poseStack, float partialTick) {
        AriseNB5Config config = AriseNB5Config.get();
        if (!config.enableAnimations) return;

        TrackedAnimation anim = getAnimationForState(state);
        if (anim == null || anim.action == ParkourActionType.NONE || anim.ticks <= 0) return;

        float subTickTime = Math.max(0.0f, anim.ticks - partialTick);

        switch (anim.action) {
            case ROLL -> {
                // Smooth 360-degree forward tumble around entity center of mass (+0.9 Y)
                float progress = 1.0f - (subTickTime / 14.0f);
                float angle = progress * 360.0f;
                poseStack.translate(0.0f, 0.9f, 0.0f);
                poseStack.rotateDegrees(Axis.XP, angle);
                poseStack.translate(0.0f, -0.9f, 0.0f);
            }
            case DODGE -> {
                // Evasive side-tilt
                float progress = subTickTime / 10.0f;
                float tilt = progress * 15.0f;
                poseStack.rotateDegrees(Axis.ZP, tilt);
            }
            case BREAKFALL -> {
                // Absorption compression
                float progress = subTickTime / 10.0f;
                poseStack.translate(0.0f, -0.2f * progress, 0.0f);
            }
            default -> {}
        }
    }

    /**
     * Poses player model limbs (arms and legs) during ledge grabbing, climbing, vaulting, and sliding.
     * Interpolates with partial ticks for fluid animations.
     */
    public static void applyPlayerModelAnim(PlayerModel model, AvatarRenderState state, float partialTick) {
        AriseNB5Config config = AriseNB5Config.get();
        if (!config.enableAnimations) return;

        TrackedAnimation anim = getAnimationForState(state);
        if (anim == null || anim.action == ParkourActionType.NONE) return;

        float subTickTime = Math.max(0.0f, anim.ticks - partialTick);

        switch (anim.action) {
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
                float progress = 1.0f - (subTickTime / 12.0f);
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
        if (action == ParkourActionType.NONE || ticks <= 0) return;

        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float subTickTime = Math.max(0.0f, ticks - partialTick);

        switch (action) {
            case ROLL -> {
                if (config.enableCameraRotationRoll) {
                    float progress = 1.0f - (subTickTime / 14.0f);
                    float pitchRoll = (float) Math.sin(progress * Math.PI) * 20.0f;
                    poseStack.rotateDegrees(Axis.XP, pitchRoll);
                }
            }
            case SLIDE -> {
                float dip = (subTickTime / 15.0f) * 0.35f;
                poseStack.translate(0.0f, -dip, 0.0f);
            }
            case BREAKFALL -> {
                float dip = (subTickTime / 10.0f) * 0.25f;
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
