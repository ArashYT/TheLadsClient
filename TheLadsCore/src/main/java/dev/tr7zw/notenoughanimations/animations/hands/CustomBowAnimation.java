/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.tr7zw.transition.mc.EntityUtil
 *  lombok.Generated
 *  net.minecraft.client.model.HumanoidModel$ArmPose
 *  net.minecraft.client.model.geom.ModelPart
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.util.Mth
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.HumanoidArm
 */
package dev.tr7zw.notenoughanimations.animations.hands;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.animations.hands.VanillaProjectileWeaponAnimation;
import dev.tr7zw.notenoughanimations.util.AnimationUtil;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import dev.tr7zw.notenoughanimations.versionless.animations.BowAnimation;
import dev.tr7zw.transition.mc.EntityUtil;
import java.util.EnumSet;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;

public class CustomBowAnimation
extends VanillaProjectileWeaponAnimation {
    private final EnumSet<HumanoidModel.ArmPose> twoHandedAnimations = EnumSet.of(HumanoidModel.ArmPose.BOW_AND_ARROW);

    @Override
    public boolean isEnabled() {
        return NEABaseMod.config.bowAnimation == BowAnimation.CUSTOM_V1;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        int invert;
        boolean bowInLeftHand;
        ModelPart mainArm = model.rightArm;
        ModelPart offArm = model.leftArm;
        BodyPart mainPart = BodyPart.RIGHT_ARM;
        BodyPart offPart = BodyPart.LEFT_ARM;
        boolean bl = bowInLeftHand = entity.getMainArm() == HumanoidArm.RIGHT && entity.getUsedItemHand() == InteractionHand.OFF_HAND || entity.getMainArm() == HumanoidArm.LEFT && entity.getUsedItemHand() == InteractionHand.MAIN_HAND;
        if (bowInLeftHand) {
            mainArm = model.leftArm;
            offArm = model.rightArm;
            mainPart = BodyPart.LEFT_ARM;
            offPart = BodyPart.RIGHT_ARM;
        }
        int n = invert = bowInLeftHand ? -1 : 1;
        if (part == mainPart) {
            mainArm.yRot = (float)invert * Mth.clamp((float)(-0.1f + AnimationUtil.wrapDegrees(-model.head.xRot)), (float)-1.25f, (float)0.5f);
            mainArm.xRot = Mth.clamp((float)(-1.5707964f + (float)invert * AnimationUtil.wrapDegrees(model.head.yRot)), (float)-2.0f, (float)0.0f);
            mainArm.zRot += (float)invert * 1.5f;
        }
        if (part == offPart) {
            offArm.yRot = (float)invert * Mth.clamp((float)(0.1f + AnimationUtil.wrapDegrees(-model.head.xRot)), (float)-1.05f, (float)0.7f);
            offArm.xRot = Mth.clamp((float)(-1.5707964f + (float)invert * AnimationUtil.wrapDegrees(model.head.yRot) + 0.8f), (float)-1.05f, (float)-0.65f);
            offArm.zRot += (float)invert * 1.5f;
        }
        if (part == BodyPart.BODY && NEABaseMod.config.customBowRotationLock) {
            if (bowInLeftHand) {
                entity.yBodyRot = EntityUtil.getYRot((Entity)entity) + 40.0f;
                entity.yBodyRotO = entity.yRotO + 40.0f;
            } else {
                entity.yBodyRot = EntityUtil.getYRot((Entity)entity) - 40.0f;
                entity.yBodyRotO = entity.yRotO - 40.0f;
            }
        }
    }

    @Override
    public EnumSet<HumanoidModel.ArmPose> getTwoHandedAnimations() {
        return this.twoHandedAnimations;
    }
}

