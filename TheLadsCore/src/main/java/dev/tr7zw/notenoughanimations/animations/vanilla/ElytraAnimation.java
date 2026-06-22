/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Pose
 */
package dev.tr7zw.notenoughanimations.animations.vanilla;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.api.PoseOverwrite;
import dev.tr7zw.notenoughanimations.util.RenderStateHolder;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

public class ElytraAnimation
extends BasicAnimation
implements PoseOverwrite {
    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        return entity.getPose() == Pose.FALL_FLYING;
    }

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer entity, PlayerData data) {
        return BodyPart.values();
    }

    @Override
    public int getPriority(AbstractClientPlayer entity, PlayerData data) {
        return 3000;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        if (!NEABaseMod.config.tweakElytraAnimation) {
            return;
        }
        float k = (float)entity.getDeltaMovement().lengthSqr();
        k /= 0.2f;
        if ((k *= k * k) < 1.0f) {
            k = 1.0f;
        }
        float moveOut = 0.1507964f / k;
        moveOut = Math.min(moveOut, 0.25f);
        moveOut = Math.max(moveOut, 0.1f);
        if (part == BodyPart.LEFT_ARM) {
            model.leftArm.xRot = Mth.cos((double)(tickCounter * 0.6662f)) * 0.5f / k;
            model.leftArm.zRot = -moveOut;
        }
        if (part == BodyPart.RIGHT_ARM) {
            model.rightArm.xRot = Mth.cos((double)(tickCounter * 0.6662f + (float)Math.PI)) * 0.5f / k;
            model.rightArm.zRot = moveOut;
        }
        if (part == BodyPart.LEFT_LEG) {
            model.leftLeg.xRot = Mth.cos((double)(tickCounter * 0.6662f + (float)Math.PI)) * 0.7f / k;
            model.leftLeg.zRot = -moveOut;
        }
        if (part == BodyPart.RIGHT_LEG) {
            model.rightLeg.xRot = Mth.cos((double)(tickCounter * 0.6662f)) * 0.7f / k;
            model.rightLeg.zRot = moveOut;
        }
    }

    @Override
    public void updateState(AbstractClientPlayer entity, PlayerData data, PlayerModel playerModel) {
        if (this.isValid(entity, data)) {
            RenderStateHolder.RenderStateData stateData = data.getData(RenderStateHolder.INSTANCE, RenderStateHolder.RenderStateData::new);
            stateData.renderState.isCrouching = false;
        }
    }
}

