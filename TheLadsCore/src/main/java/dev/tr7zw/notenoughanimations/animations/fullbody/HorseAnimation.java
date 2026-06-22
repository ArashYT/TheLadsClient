/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.entity.animal.equine.AbstractHorse
 */
package dev.tr7zw.notenoughanimations.animations.fullbody;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.util.AnimationUtil;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

public class HorseAnimation
extends BasicAnimation {
    private final BodyPart[] bothHands = new BodyPart[]{BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM, BodyPart.BODY};
    private final BodyPart[] fullBody = new BodyPart[]{BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM, BodyPart.BODY, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG};

    @Override
    public boolean isEnabled() {
        return NEABaseMod.config.enableHorseAnimation;
    }

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        AbstractHorse horse;
        Entity entity2;
        return entity.isPassenger() && (entity2 = entity.getVehicle()) instanceof AbstractHorse && (horse = (AbstractHorse)entity2).isSaddled();
    }

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer entity, PlayerData data) {
        return NEABaseMod.config.enableHorseLegAnimation ? this.fullBody : this.bothHands;
    }

    @Override
    public int getPriority(AbstractClientPlayer entity, PlayerData data) {
        return 1500;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        if (part == BodyPart.BODY) {
            return;
        }
        if (part == BodyPart.LEFT_ARM && AnimationUtil.isSwingingArm(entity, part)) {
            return;
        }
        if (part == BodyPart.RIGHT_ARM && AnimationUtil.isSwingingArm(entity, part)) {
            return;
        }
        HumanoidArm arm = part == BodyPart.LEFT_ARM ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        AbstractHorse horse = (AbstractHorse)entity.getVehicle();
        int id = horse.getPassengers().indexOf(entity);
        if (id == 0) {
            if (part == BodyPart.LEFT_LEG || part == BodyPart.RIGHT_LEG) {
                if (horse.isStanding()) {
                    AnimationUtil.applyTransforms(model, part, -0.8f, 0.0f, 0.8f);
                } else {
                    AnimationUtil.applyTransforms(model, part, -0.2f, 0.0f, 0.8f);
                }
            } else {
                float rotation = -Mth.cos((double)(horse.walkAnimation.position() * 0.3f));
                rotation = (float)((double)rotation * 0.1);
                AnimationUtil.applyArmTransforms(model, arm, -1.1f - rotation, -0.2f, 0.3f);
            }
        }
    }
}

