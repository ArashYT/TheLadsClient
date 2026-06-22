/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.entity.projectile.ProjectileUtil
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.EntityHitResult
 *  net.minecraft.world.phys.Vec3
 */
package dev.tr7zw.notenoughanimations.animations.hands;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.util.AnimationUtil;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class HugAnimation
extends BasicAnimation {
    private final BodyPart[] arms = new BodyPart[]{BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM};

    @Override
    public boolean isEnabled() {
        return NEABaseMod.config.huggingAnimation;
    }

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        AABB aABB;
        if (!entity.isCrouching()) {
            return false;
        }
        double d = 1.0;
        Vec3 vec3 = entity.getEyePosition(0.0f);
        Vec3 vec32 = entity.getViewVector(1.0f);
        Vec3 vec33 = vec3.add(vec32.x * d, vec32.y * d, vec32.z * d);
        EntityHitResult entHit = ProjectileUtil.getEntityHitResult((Entity)entity, (Vec3)vec3, (Vec3)vec33, (AABB)(aABB = entity.getBoundingBox().expandTowards(vec32.scale(d)).inflate(1.0, 1.0, 1.0)), en -> !en.isSpectator(), (double)d);
        if (entHit != null && entHit.getEntity().getType() == net.minecraft.world.entity.EntityTypes.PLAYER) {
            AbstractClientPlayer otherPlayer = (AbstractClientPlayer)entHit.getEntity();
            double dif = otherPlayer.getY() - entity.getY();
            if (otherPlayer.isCrouching() && Math.abs(dif) < 0.3) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer entity, PlayerData data) {
        return this.arms;
    }

    @Override
    public int getPriority(AbstractClientPlayer entity, PlayerData data) {
        return 2100;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        if (part == BodyPart.LEFT_ARM) {
            AnimationUtil.applyArmTransforms(model, HumanoidArm.LEFT, -1.0f, -0.2f, 0.3f);
        }
        if (part == BodyPart.RIGHT_ARM) {
            AnimationUtil.applyArmTransforms(model, HumanoidArm.RIGHT, -1.5f, -0.2f, 0.3f);
        }
    }
}

