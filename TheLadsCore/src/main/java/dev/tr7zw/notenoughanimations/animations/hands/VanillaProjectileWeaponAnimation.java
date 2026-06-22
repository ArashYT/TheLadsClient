/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.HumanoidModel$ArmPose
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.HumanoidArm
 */
package dev.tr7zw.notenoughanimations.animations.hands;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.util.AnimationUtil;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import java.util.EnumSet;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

public abstract class VanillaProjectileWeaponAnimation
extends BasicAnimation {
    private HumanoidModel.ArmPose rightArmPose;
    private HumanoidModel.ArmPose leftArmPose;
    private final BodyPart[] parts = new BodyPart[]{BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM, BodyPart.BODY};

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        this.rightArmPose = AnimationUtil.getArmPose(entity, entity.getMainArm() == HumanoidArm.LEFT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        this.leftArmPose = AnimationUtil.getArmPose(entity, entity.getMainArm() == HumanoidArm.RIGHT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        return this.getTwoHandedAnimations().contains(this.leftArmPose) || this.getTwoHandedAnimations().contains(this.rightArmPose);
    }

    protected abstract EnumSet<HumanoidModel.ArmPose> getTwoHandedAnimations();

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer entity, PlayerData data) {
        return this.parts;
    }

    @Override
    public int getPriority(AbstractClientPlayer entity, PlayerData data) {
        return 3200;
    }
}

