/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.tr7zw.transition.mc.EntityUtil
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.util.Mth
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.item.ItemUseAnimation
 */
package dev.tr7zw.notenoughanimations.animations.hands;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.util.AnimationUtil;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import dev.tr7zw.transition.mc.EntityUtil;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemUseAnimation;

public class EatDrinkAnimation
extends BasicAnimation {
    private BodyPart[] target;
    private final BodyPart[] left = new BodyPart[]{BodyPart.LEFT_ARM};
    private final BodyPart[] right = new BodyPart[]{BodyPart.RIGHT_ARM};

    @Override
    public boolean isEnabled() {
        return NEABaseMod.config.enableEatDrinkAnimation;
    }

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        block3: {
            block4: {
                if (entity.getUseItemRemainingTicks() <= 0) break block3;
                ItemUseAnimation action = entity.getUseItem().getUseAnimation();
                if (action == ItemUseAnimation.EAT) break block4;
                if (action != ItemUseAnimation.DRINK) break block3;
            }
            this.target = entity.getUsedItemHand() == InteractionHand.MAIN_HAND ? (entity.getMainArm() == HumanoidArm.RIGHT ? this.right : this.left) : (entity.getMainArm() == HumanoidArm.RIGHT ? this.left : this.right);
            return true;
        }
        return false;
    }

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer entity, PlayerData data) {
        return this.target;
    }

    @Override
    public int getPriority(AbstractClientPlayer entity, PlayerData data) {
        return 2500;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        HumanoidArm arm = part == BodyPart.LEFT_ARM ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        float g = (float)entity.getUseItemRemainingTicks() - delta + 1.0f;
        AnimationUtil.applyArmTransforms(model, arm, -Mth.lerp((float)(-1.0f * (EntityUtil.getXRot((Entity)entity) - 90.0f) / 180.0f), (float)1.0f, (float)2.0f) + Mth.abs((float)(Mth.cos((double)(g / 4.0f * (float)Math.PI)) * 0.2f)), -0.3f, 0.3f);
    }
}

