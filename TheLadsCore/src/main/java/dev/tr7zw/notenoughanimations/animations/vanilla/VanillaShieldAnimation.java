/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.HumanoidModel$ArmPose
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.HumanoidArm
 */
package dev.tr7zw.notenoughanimations.animations.vanilla;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.util.AnimationUtil;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

public class VanillaShieldAnimation
extends BasicAnimation {
    private HumanoidModel.ArmPose rightArmPose;
    private HumanoidModel.ArmPose leftArmPose;
    private final BodyPart[] left = new BodyPart[]{BodyPart.LEFT_ARM};
    private final BodyPart[] right = new BodyPart[]{BodyPart.RIGHT_ARM};

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        this.rightArmPose = AnimationUtil.getArmPose(entity, entity.getMainArm() == HumanoidArm.LEFT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        this.leftArmPose = AnimationUtil.getArmPose(entity, entity.getMainArm() == HumanoidArm.RIGHT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        return HumanoidModel.ArmPose.BLOCK == this.leftArmPose || HumanoidModel.ArmPose.BLOCK == this.rightArmPose;
    }

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer entity, PlayerData data) {
        if (HumanoidModel.ArmPose.BLOCK == this.leftArmPose) {
            return this.left;
        }
        if (HumanoidModel.ArmPose.BLOCK == this.rightArmPose) {
            return this.right;
        }
        return new BodyPart[0];
    }

    @Override
    public int getPriority(AbstractClientPlayer entity, PlayerData data) {
        return 3100;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
    }
}

