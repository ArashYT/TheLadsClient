/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.util.Mth
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.entity.Pose
 *  net.minecraft.world.entity.player.Player
 */
package dev.tr7zw.notenoughanimations.animations.fullbody;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.util.RenderStateHolder;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public class CrawlingAnimation
extends BasicAnimation {
    private BodyPart[] bodyParts = new BodyPart[]{BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG};
    private final float speedMul = 2.5f;
    private float swimAmount;
    private float attackTime;
    private float animationStep;
    private float animationStep2;
    private HumanoidArm humanoidArm;
    private float m;
    private float n;
    private float armMoveHight = 0.3707964f;
    private final float legPitch = 0.15f;
    private final float r = 0.33333334f;

    @Override
    public boolean isEnabled() {
        return NEABaseMod.config.enableCrawlingAnimation;
    }

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        return entity.getPose() == Pose.SWIMMING && !entity.isInWater();
    }

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer entity, PlayerData data) {
        return this.bodyParts;
    }

    @Override
    public int getPriority(AbstractClientPlayer entity, PlayerData data) {
        return 350;
    }

    @Override
    protected void precalculate(AbstractClientPlayer entity, PlayerData data, PlayerModel model, float delta, float swing) {
        RenderStateHolder.RenderStateData stateData = data.getData(RenderStateHolder.INSTANCE, RenderStateHolder.RenderStateData::new);
        this.swimAmount = stateData.renderState.swimAmount;
        this.attackTime = stateData.renderState.attackTime;
        if (this.swimAmount > 0.0f) {
            this.animationStep = swing * 2.5f % 26.0f;
            this.animationStep2 = this.animationStep + 13.0f;
            this.animationStep2 %= 26.0f;
            this.humanoidArm = this.getAttackArm((Player)entity);
            this.m = this.humanoidArm == HumanoidArm.RIGHT && this.attackTime > 0.0f ? 0.0f : this.swimAmount;
            this.n = this.humanoidArm == HumanoidArm.LEFT && this.attackTime > 0.0f ? 0.0f : this.swimAmount;
        }
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        if (this.swimAmount > 0.0f) {
            float p;
            float o;
            if (part == BodyPart.RIGHT_ARM) {
                if (this.animationStep < 14.0f) {
                    model.rightArm.xRot = Mth.lerp((float)this.m, (float)model.rightArm.xRot, (float)0.0f);
                    model.rightArm.yRot = Mth.lerp((float)this.m, (float)model.rightArm.yRot, (float)((float)Math.PI));
                    model.rightArm.zRot = Mth.lerp((float)this.m, (float)model.rightArm.zRot, (float)((float)Math.PI - 1.8707964f * this.quadraticArmUpdate(this.animationStep) / this.quadraticArmUpdate(14.0f)));
                } else if (this.animationStep >= 14.0f && this.animationStep < 24.0f) {
                    o = (this.animationStep - 14.0f) / 10.0f;
                    model.rightArm.xRot = Mth.lerp((float)this.m, (float)model.rightArm.xRot, (float)(-this.armMoveHight * o));
                    model.rightArm.yRot = Mth.lerp((float)this.m, (float)model.rightArm.yRot, (float)((float)Math.PI));
                    model.rightArm.zRot = Mth.lerp((float)this.m, (float)model.rightArm.zRot, (float)(1.2707963f + 1.8707964f * o));
                } else if (this.animationStep >= 24.0f && this.animationStep < 26.0f) {
                    p = (this.animationStep - 24.0f) / 2.0f;
                    model.rightArm.xRot = Mth.lerp((float)this.m, (float)model.rightArm.xRot, (float)(-this.armMoveHight + this.armMoveHight * p));
                    model.rightArm.yRot = Mth.lerp((float)this.m, (float)model.rightArm.yRot, (float)((float)Math.PI));
                    model.rightArm.zRot = Mth.lerp((float)this.m, (float)model.rightArm.zRot, (float)((float)Math.PI));
                }
            }
            if (part == BodyPart.LEFT_ARM) {
                if (this.animationStep2 < 14.0f) {
                    model.leftArm.xRot = this.rotlerpRad(this.n, model.leftArm.xRot, 0.0f);
                    model.leftArm.yRot = this.rotlerpRad(this.n, model.leftArm.yRot, (float)Math.PI);
                    model.leftArm.zRot = this.rotlerpRad(this.n, model.leftArm.zRot, (float)Math.PI + 1.8707964f * this.quadraticArmUpdate(this.animationStep2) / this.quadraticArmUpdate(14.0f));
                } else if (this.animationStep2 >= 14.0f && this.animationStep2 < 24.0f) {
                    o = (this.animationStep2 - 14.0f) / 10.0f;
                    model.leftArm.xRot = this.rotlerpRad(this.n, model.leftArm.xRot, -this.armMoveHight * o);
                    model.leftArm.yRot = this.rotlerpRad(this.n, model.leftArm.yRot, (float)Math.PI);
                    model.leftArm.zRot = this.rotlerpRad(this.n, model.leftArm.zRot, 5.012389f - 1.8707964f * o);
                } else if (this.animationStep2 >= 24.0f && this.animationStep2 < 26.0f) {
                    p = (this.animationStep2 - 24.0f) / 2.0f;
                    model.leftArm.xRot = this.rotlerpRad(this.n, model.leftArm.xRot, -this.armMoveHight + this.armMoveHight * p);
                    model.leftArm.yRot = this.rotlerpRad(this.n, model.leftArm.yRot, (float)Math.PI);
                    model.leftArm.zRot = this.rotlerpRad(this.n, model.leftArm.zRot, (float)Math.PI);
                }
            }
        }
        tickCounter *= 2.5f;
        if (part == BodyPart.LEFT_LEG) {
            model.leftLeg.xRot = Mth.lerp((float)this.swimAmount, (float)model.leftLeg.xRot, (float)(0.15f * Mth.cos((double)(tickCounter * 0.33333334f + (float)Math.PI))));
            model.leftLeg.zRot = -0.1507964f;
        }
        if (part == BodyPart.RIGHT_LEG) {
            model.rightLeg.xRot = Mth.lerp((float)this.swimAmount, (float)model.rightLeg.xRot, (float)(0.15f * Mth.cos((double)(tickCounter * 0.33333334f))));
            model.rightLeg.zRot = 0.1507964f;
        }
    }

    private float rotlerpRad(float f, float g, float h) {
        float i = (h - g) % ((float)Math.PI * 2);
        if (i < (float)(-Math.PI)) {
            i += (float)Math.PI * 2;
        }
        if (i >= (float)Math.PI) {
            i -= (float)Math.PI * 2;
        }
        return g + f * i;
    }

    private float quadraticArmUpdate(float f) {
        return -65.0f * f + f * f;
    }

    private HumanoidArm getAttackArm(Player livingEntity) {
        HumanoidArm humanoidArm = livingEntity.getMainArm();
        return livingEntity.swingingArm == InteractionHand.MAIN_HAND ? humanoidArm : humanoidArm.getOpposite();
    }
}

