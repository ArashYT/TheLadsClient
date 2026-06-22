/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.tr7zw.transition.mc.EntityUtil
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.entity.TamableAnimal
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
import dev.tr7zw.transition.mc.EntityUtil;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class PetAnimation
extends BasicAnimation {
    private Entity targetPet = null;
    private final BodyPart[] leftHanded = new BodyPart[]{BodyPart.LEFT_ARM};
    private final BodyPart[] rightHanded = new BodyPart[]{BodyPart.RIGHT_ARM};

    @Override
    public boolean isEnabled() {
        return NEABaseMod.config.petAnimation;
    }

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        TamableAnimal pet;
        double dif;
        AABB aABB;
        if (!entity.isCrouching()) {
            return false;
        }
        double d = 1.0;
        Vec3 vec3 = entity.getEyePosition(0.0f);
        Vec3 vec32 = entity.getViewVector(1.0f);
        Vec3 vec33 = vec3.add(vec32.x * d, vec32.y * d, vec32.z * d);
        EntityHitResult entHit = ProjectileUtil.getEntityHitResult((Entity)entity, (Vec3)vec3, (Vec3)vec33, (AABB)(aABB = entity.getBoundingBox().expandTowards(vec32.scale(d)).inflate(1.0, 1.0, 1.0)), en -> !en.isSpectator(), (double)d);
        if (entHit != null && (entHit.getEntity().getType() == net.minecraft.world.entity.EntityTypes.WOLF || entHit.getEntity().getType() == net.minecraft.world.entity.EntityTypes.CAT) && Math.abs(dif = (pet = (TamableAnimal)entHit.getEntity()).getY() - entity.getY()) < 0.6) {
            this.targetPet = pet;
            return true;
        }
        this.targetPet = null;
        return false;
    }

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer entity, PlayerData data) {
        return entity.getMainArm() == HumanoidArm.RIGHT ? this.rightHanded : this.leftHanded;
    }

    @Override
    public int getPriority(AbstractClientPlayer entity, PlayerData data) {
        return 2100;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        if (Math.random() < 0.005) {
            this.targetPet.handleEntityEvent((byte)18);
        }
        HumanoidArm arm = part == BodyPart.LEFT_ARM ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        AnimationUtil.applyArmTransforms(model, arm, -Mth.lerp((float)(-1.0f * (EntityUtil.getXRot((Entity)entity) - 90.0f) / 180.0f), (float)1.0f, (float)2.0f), -0.6f, 0.3f + Mth.sin((double)((float)(System.currentTimeMillis() % 20000L) / 60.0f)) * 0.2f);
        this.targetPet = null;
    }
}

