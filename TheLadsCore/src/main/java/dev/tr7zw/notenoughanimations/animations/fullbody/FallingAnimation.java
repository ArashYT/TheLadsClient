/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 */
package dev.tr7zw.notenoughanimations.animations.fullbody;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.util.NMSWrapper;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import dev.tr7zw.notenoughanimations.versionless.animations.DataHolder;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class FallingAnimation
extends BasicAnimation
implements DataHolder<FallingAnimation.FallingData> {
    @Override
    public boolean isEnabled() {
        return NEABaseMod.config.fallingAnimation;
    }

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        if (!(entity.isFallFlying() || NMSWrapper.onGround((Entity)entity) || entity.onClimbable() || entity.getAbilities().flying || entity.isSwimming())) {
            FallingData fallData = data.getData(this, () -> new FallingData(entity.getY()));
            if (entity instanceof LocalPlayer) {
                fallData.fallingSpeed = (float)(entity.getDeltaMovement().lengthSqr() / 11.0);
                return entity.fallDistance > 3.0;
            }
            if (entity.getY() == fallData.lastY) {
                return fallData.fallingSpeed > 0.14285715f;
            }
            fallData.fallingSpeed = (float)(fallData.lastY - entity.getY()) / 3.5f;
            fallData.lastY = entity.getY();
            return fallData.fallingSpeed > 0.14285715f;
        }
        return false;
    }

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer entity, PlayerData data) {
        return BodyPart.values();
    }

    @Override
    public int getPriority(AbstractClientPlayer entity, PlayerData data) {
        return 400;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        FallingData fallData = data.getData(this, () -> new FallingData(entity.getY()));
        float moveSqrt = fallData.fallingSpeed;
        float armsMove = Math.min(1.0f, moveSqrt * 2.0f);
        moveSqrt = Math.min(1.0f, moveSqrt);
        float moveOutArms = 1.9f * armsMove;
        float moveOutLegs = 0.6f * moveSqrt;
        float movement = (float)entity.tickCount + delta;
        if (part == BodyPart.LEFT_ARM) {
            model.leftArm.xRot = Mth.cos((double)(movement * 0.6662f)) * moveSqrt;
            model.leftArm.zRot = -moveOutArms;
        }
        if (part == BodyPart.RIGHT_ARM) {
            model.rightArm.xRot = Mth.cos((double)(movement * 0.6662f + (float)Math.PI)) * moveSqrt;
            model.rightArm.zRot = moveOutArms;
        }
        if (part == BodyPart.LEFT_LEG) {
            model.leftLeg.xRot = Mth.cos((double)(movement * 0.6662f + (float)Math.PI)) * 1.4f * moveSqrt;
            model.leftLeg.zRot = -moveOutLegs;
        }
        if (part == BodyPart.RIGHT_LEG) {
            model.rightLeg.xRot = Mth.cos((double)(movement * 0.6662f)) * 1.4f * moveSqrt;
            model.rightLeg.zRot = moveOutLegs;
        }
    }

    public static class FallingData {
        public double lastY = 0.0;
        public float fallingSpeed = 0.0f;

        public FallingData(double y) {
            this.lastY = y;
        }
    }
}

