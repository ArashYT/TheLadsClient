/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  net.minecraft.client.model.HumanoidModel$ArmPose
 *  net.minecraft.client.model.geom.ModelPart
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.util.Mth
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.HumanoidArm
 */
package dev.tr7zw.notenoughanimations.animations.hands;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.animations.hands.VanillaProjectileWeaponAnimation;
import dev.tr7zw.notenoughanimations.util.AnimationUtil;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import java.util.EnumSet;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

public class ClampCrossbowAnimations
extends VanillaProjectileWeaponAnimation {
    private final EnumSet<HumanoidModel.ArmPose> twoHandedAnimations = EnumSet.of(HumanoidModel.ArmPose.CROSSBOW_HOLD, HumanoidModel.ArmPose.CROSSBOW_CHARGE);

    @Override
    public boolean isEnabled() {
        return NEABaseMod.config.clampCrossbowAnimations;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        boolean bowInLeftHand;
        ModelPart mainArm = model.rightArm;
        ModelPart offArm = model.leftArm;
        BodyPart mainPart = BodyPart.RIGHT_ARM;
        BodyPart offPart = BodyPart.LEFT_ARM;
        boolean bl = bowInLeftHand = entity.getMainArm() == HumanoidArm.RIGHT && entity.getUsedItemHand() == InteractionHand.OFF_HAND || entity.getMainArm() == HumanoidArm.LEFT && entity.getUsedItemHand() == InteractionHand.MAIN_HAND || entity.getMainArm() == HumanoidArm.RIGHT && AnimationUtil.isChargedCrossbow(entity.getOffhandItem()) || entity.getMainArm() == HumanoidArm.LEFT && AnimationUtil.isChargedCrossbow(entity.getMainHandItem());
        if (bowInLeftHand) {
            mainArm = model.leftArm;
            offArm = model.rightArm;
            mainPart = BodyPart.LEFT_ARM;
            offPart = BodyPart.RIGHT_ARM;
        }
        if (part == mainPart) {
            mainArm.xRot = Mth.clamp((float)AnimationUtil.wrapDegrees(mainArm.xRot), (float)-1.75f, (float)0.0f);
        }
        if (part == offPart) {
            offArm.xRot = Mth.clamp((float)AnimationUtil.wrapDegrees(offArm.xRot), (float)-1.75f, (float)0.0f);
        }
    }

    @Override
    public EnumSet<HumanoidModel.ArmPose> getTwoHandedAnimations() {
        return this.twoHandedAnimations;
    }
}

