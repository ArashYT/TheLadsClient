/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.tr7zw.transition.mc.EntityUtil
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.util.Mth
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 */
package dev.tr7zw.notenoughanimations.animations.hands;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.util.AnimationUtil;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import dev.tr7zw.notenoughanimations.versionless.animations.HoldUpModes;
import dev.tr7zw.transition.mc.EntityUtil;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class LookAtItemAnimation
extends BasicAnimation {
    private Set<Item> holdingItems = new HashSet<Item>();
    private final BodyPart[] bothHands = new BodyPart[]{BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM};
    private final BodyPart[] left = new BodyPart[]{BodyPart.LEFT_ARM};
    private final BodyPart[] right = new BodyPart[]{BodyPart.RIGHT_ARM};
    private BodyPart[] target = this.bothHands;

    @Override
    public boolean isEnabled() {
        this.bind();
        return NEABaseMod.config.holdUpItemsMode != HoldUpModes.NONE && !this.holdingItems.isEmpty();
    }

    private void bind() {
        this.holdingItems.clear();
        this.holdingItems.addAll(AnimationUtil.parseItemList(NEABaseMod.config.holdingItems));
    }

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        boolean leftArm;
        if (NEABaseMod.config.holdUpOnlySelf && entity != Minecraft.getInstance().player) {
            return false;
        }
        boolean allItems = NEABaseMod.config.holdUpItemsMode == HoldUpModes.ALL;
        ItemStack itemInRightHand = entity.getItemInHand(entity.getMainArm() == HumanoidArm.LEFT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        ItemStack itemInLeftHand = entity.getItemInHand(entity.getMainArm() == HumanoidArm.RIGHT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        boolean rightArm = NEABaseMod.config.holdUpItemsMode == HoldUpModes.CONFIG && this.holdingItems.contains(itemInRightHand.getItem()) || NEABaseMod.config.holdUpItemsMode == HoldUpModes.CONFIG_INVERTED && !this.holdingItems.contains(itemInRightHand.getItem()) || allItems && !itemInRightHand.isEmpty() && (!entity.swinging || entity.getMainArm() != HumanoidArm.RIGHT);
        boolean bl = leftArm = NEABaseMod.config.holdUpItemsMode == HoldUpModes.CONFIG && this.holdingItems.contains(itemInLeftHand.getItem()) || NEABaseMod.config.holdUpItemsMode == HoldUpModes.CONFIG_INVERTED && !this.holdingItems.contains(itemInLeftHand.getItem()) || allItems && !itemInLeftHand.isEmpty() && (!entity.swinging || entity.getMainArm() != HumanoidArm.LEFT);
        if (rightArm && leftArm && !entity.swinging) {
            this.target = this.bothHands;
            return true;
        }
        if (rightArm && (!entity.swinging || entity.swingingArm != (entity.getMainArm() == HumanoidArm.LEFT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND))) {
            this.target = this.right;
            return true;
        }
        if (leftArm && (!entity.swinging || entity.swingingArm != (entity.getMainArm() == HumanoidArm.RIGHT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND))) {
            this.target = this.left;
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
        return 300;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        HumanoidArm arm = part == BodyPart.LEFT_ARM ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        switch (NEABaseMod.config.holdUpTarget) {
            case NONE: {
                AnimationUtil.applyArmTransforms(model, arm, -NEABaseMod.config.holdUpItemOffset - Mth.lerp((float)(-1.0f * (EntityUtil.getXRot((Entity)entity) - 90.0f) / 180.0f), (float)1.0f, (float)1.5f), -0.2f, 0.3f);
                break;
            }
            case CAMERA: {
                float invert = part == BodyPart.LEFT_ARM ? -1.0f : 1.0f;
                AnimationUtil.applyArmTransforms(model, arm, Mth.clamp((float)(-1.5707964f + model.head.xRot), (float)-2.5f, (float)0.0f), Mth.clamp((float)(NEABaseMod.config.holdUpCameraOffset + model.head.yRot * invert), (float)-0.2f, (float)Math.max(0.2f, NEABaseMod.config.holdUpCameraOffset)), 0.1f);
            }
        }
    }
}

