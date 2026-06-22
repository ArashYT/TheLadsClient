/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.tr7zw.transition.mc.GeneralUtil
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.level.block.Blocks
 */
package dev.tr7zw.notenoughanimations.animations.fullbody;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.util.AnimationUtil;
import dev.tr7zw.notenoughanimations.util.NMSWrapper;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import dev.tr7zw.transition.mc.GeneralUtil;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.block.Blocks;

public class FreezingAnimation
extends BasicAnimation {
    private BodyPart[] parts = new BodyPart[]{BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM};

    @Override
    public boolean isEnabled() {
        return NEABaseMod.config.freezingAnimation;
    }

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        return entity.canFreeze() && GeneralUtil.getWorld().getBlockStatesIfLoaded(entity.getBoundingBox().deflate(1.0E-6)).anyMatch(blockState -> blockState.is(Blocks.POWDER_SNOW) || blockState.is(Blocks.POWDER_SNOW_CAULDRON));
    }

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer entity, PlayerData data) {
        return this.parts;
    }

    @Override
    public int getPriority(AbstractClientPlayer entity, PlayerData data) {
        return 400;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        float position;
        if (entity.swinging && NMSWrapper.getArm(entity, entity.swingingArm) == part) {
            return;
        }
        if (part == BodyPart.LEFT_ARM) {
            position = (float)(Math.random() / 10.0 + (double)-1.3f);
            AnimationUtil.applyArmTransforms(model, HumanoidArm.LEFT, -0.6f, 0.2f, position);
        }
        if (part == BodyPart.RIGHT_ARM) {
            position = (float)(Math.random() / 10.0 + -1.0);
            AnimationUtil.applyArmTransforms(model, HumanoidArm.RIGHT, -0.5f, 0.2f, position);
        }
    }
}

