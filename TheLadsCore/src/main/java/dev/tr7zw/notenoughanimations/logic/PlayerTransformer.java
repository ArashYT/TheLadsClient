/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.tr7zw.transition.mc.GeneralUtil
 *  net.minecraft.client.CameraType
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.model.geom.ModelPart
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.tr7zw.notenoughanimations.logic;

import dev.tr7zw.notenoughanimations.NEAnimationsLoader;
import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.util.AnimationUtil;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.RotationLock;
import dev.tr7zw.transition.mc.GeneralUtil;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class PlayerTransformer {
    public static final int ENTRY_SIZE = 9;
    public static final int ENTRY_AMOUNT = 5;
    private boolean doneLatebind = false;
    private final Minecraft mc = Minecraft.getInstance();
    private int tickId = 0;
    private boolean renderingFirstPersonArm = false;
    private float deltaTick = 0.0f;

    public void updateModel(AbstractClientPlayer entity, PlayerModel model, float swing, CallbackInfo info) {
        if (!this.doneLatebind) {
            this.lateBind();
        }
        if (this.mc.level == null || this.renderingFirstPersonArm) {
            return;
        }
        NEAnimationsLoader.INSTANCE.animationProvider.applyAnimations(entity, model, this.deltaTick, swing);
        if (entity instanceof PlayerData) {
            boolean isCameraEntity;
            PlayerData data = (PlayerData)entity;
            float[] last = data.getLastRotations();
            int passedTicks = data.isUpdated(this.tickId);
            boolean differentFrame = passedTicks != 0;
            float timePassed = (float)passedTicks * 50.0f;
            if (NEABaseMod.config.enableAnimationSmoothing) {
                float speed = NEABaseMod.config.animationSmoothingSpeed;
                this.interpolate(model.leftArm, last, 0, timePassed, differentFrame, speed, this.deltaTick);
                this.interpolate(model.rightArm, last, 9, timePassed, differentFrame, speed, this.deltaTick);
                if (!NEABaseMod.config.disableLegSmoothing) {
                    this.interpolate(model.leftLeg, last, 18, timePassed, differentFrame, speed, this.deltaTick);
                    this.interpolate(model.rightLeg, last, 27, timePassed, differentFrame, speed, this.deltaTick);
                }
            }
            boolean bl = isCameraEntity = entity == GeneralUtil.getCameraEntity();
            if (!(!isCameraEntity && !NEABaseMod.config.applyRotationLockToEveryone || data.isDisableBodyRotation() || isCameraEntity && NEABaseMod.config.limitRotationLockToFP && this.mc.options.getCameraType() != CameraType.FIRST_PERSON)) {
                if ((NEABaseMod.config.rotationLock == RotationLock.SMOOTH || NEABaseMod.config.rotationLock == RotationLock.NONE && data.isRotateBodyToHead()) && entity.getVehicle() == null) {
                    this.interpolateYawBodyHead(entity, last, 36, timePassed, differentFrame, 0.5f);
                } else if (NEABaseMod.config.rotationLock == RotationLock.FIXED && entity.getVehicle() == null && differentFrame) {
                    entity.yBodyRot = entity.yHeadRot;
                    entity.yBodyRotO = entity.yHeadRotO;
                } else if (differentFrame) {
                    last[36] = entity.yBodyRot;
                    last[37] = entity.yBodyRotO;
                }
            }
            data.setUpdated(this.tickId);
        }
    }

    public void preUpdate(AbstractClientPlayer livingEntity, PlayerModel playerModel, float swing, CallbackInfo info) {
        if (this.mc.level == null || this.renderingFirstPersonArm) {
            return;
        }
        NEAnimationsLoader.INSTANCE.animationProvider.preUpdate(livingEntity, playerModel);
    }

    private void lateBind() {
        NEAnimationsLoader.INSTANCE.animationProvider.refreshEnabledAnimations();
        this.doneLatebind = true;
    }

    public void nextTick() {
        ++this.tickId;
    }

    public void setDeltaTick(float delta) {
        this.deltaTick = delta;
    }

    public void renderingFirstPersonArm(boolean flag) {
        this.renderingFirstPersonArm = flag;
    }

    private void interpolate(ModelPart model, float[] last, int offset, float timePassed, boolean differentFrame, float speed, float delta) {
        if (timePassed > 50.0f) {
            float f = model.xRot;
            last[offset + 6] = f;
            last[offset + 3] = f;
            last[offset] = f;
            float f2 = model.yRot;
            last[offset + 7] = f2;
            last[offset + 4] = f2;
            last[offset + 1] = f2;
            float f3 = model.zRot;
            last[offset + 8] = f3;
            last[offset + 5] = f3;
            last[offset + 2] = f3;
            this.cleanInvalidData(last, offset);
            return;
        }
        if (!differentFrame) {
            last[offset + 6] = AnimationUtil.lerpAngle(delta, last[offset + 3], last[offset]);
            last[offset + 7] = AnimationUtil.lerpAngle(delta, last[offset + 4], last[offset + 1]);
            last[offset + 8] = AnimationUtil.lerpAngle(delta, last[offset + 5], last[offset + 2]);
            model.xRot = last[offset + 6];
            model.yRot = last[offset + 7];
            model.zRot = last[offset + 8];
            return;
        }
        last[offset + 3] = last[offset] = last[offset + 6];
        float f = last[offset + 7];
        last[offset + 1] = f;
        last[offset + 4] = f;
        float f4 = last[offset + 8];
        last[offset + 2] = f4;
        last[offset + 5] = f4;
        float amount = speed;
        amount = Math.min(amount, 1.0f);
        amount = Math.max(amount, 0.0f);
        last[offset] = AnimationUtil.interpolateRotation(model.xRot, last[offset], amount);
        last[offset + 1] = AnimationUtil.interpolateRotation(model.yRot, last[offset + 1], amount);
        last[offset + 2] = AnimationUtil.interpolateRotation(model.zRot, last[offset + 2], amount);
        this.cleanInvalidData(last, offset);
        last[offset + 6] = AnimationUtil.lerpAngle(delta, last[offset + 3], last[offset]);
        last[offset + 7] = AnimationUtil.lerpAngle(delta, last[offset + 4], last[offset + 1]);
        last[offset + 8] = AnimationUtil.lerpAngle(delta, last[offset + 5], last[offset + 2]);
        model.xRot = last[offset + 6];
        model.yRot = last[offset + 7];
        model.zRot = last[offset + 8];
    }

    private void interpolateYawBodyHead(AbstractClientPlayer entity, float[] last, int offset, float timePassed, boolean differentFrame, float speed) {
        if (!differentFrame) {
            entity.yBodyRot = last[offset];
            entity.yBodyRotO = last[offset + 1];
            return;
        }
        if (timePassed > 50.0f) {
            last[offset] = entity.yHeadRot;
            return;
        }
        if (Math.abs(AnimationUtil.wrapDegrees2(entity.yHeadRot - last[offset])) > 90.0f) {
            speed *= 0.9f;
        }
        last[offset + 1] = last[offset];
        float amount = speed;
        amount = Math.min(amount, 1.0f);
        entity.yBodyRotO = last[offset];
        last[offset] = AnimationUtil.interpolateRotation2(last[offset], entity.yHeadRot, amount);
        entity.yBodyRot = last[offset];
    }

    private void cleanInvalidData(float[] data, int offset) {
        if (Float.isNaN(data[offset])) {
            data[offset] = 0.0f;
        }
        if (Float.isNaN(data[offset + 1])) {
            data[offset + 1] = 0.0f;
        }
        if (Float.isNaN(data[offset + 2])) {
            data[offset + 2] = 0.0f;
        }
    }
}

